package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent;
import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent.State;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.ProfileImageUploadIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 프로필 전용 업로드 원장과 사용자 참조를 조정하며 S3 업로드/삭제 자체는 수행하지 않습니다. */
@Service
@RequiredArgsConstructor
public class ProfileImageUploadService {
    private final ProfileImageUploadIntentRepository intents;
    private final UserRepository users;
    private final S3ObjectStorage storage;
    private final OutboxEventPublisher publisher;
    private final ProfileImageStorageProperties properties;
    private final Clock clock;

    /** 기존 사용자 수정 TX가 나중에 rollback되어도 선등록 key는 보존합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedUpload prepare(Long userId, String extension) {
        ProfileImageUploadIntent intent = ProfileImageUploadIntent.create(userId, extension, clock.instant(), properties.uploadTimeout());
        intent.recordUrl(storage.publicUrl(intent.getS3Key()));
        intents.saveAndFlush(intent);
        return new PreparedUpload(intent.getId(), intent.getS3Key());
    }

    /** User의 기존 version 검증과 새/이전 이미지 원장 전이를 호출자 트랜잭션에 함께 커밋합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String complete(String id, User user) {
        ProfileImageUploadIntent intent = intents.findForUpdate(id).orElseThrow(ProfileImageUploadService::unavailable);
        if (!intent.canComplete(user.getId(), clock.instant())) throw unavailable();
        String previousUrl = user.getProfileImageUrl();
        user.changeProfileImageUrl(intent.getImageUrl());
        users.saveAndFlush(user);
        // 낙관적 충돌/DB 대기 뒤에 기한이 지났다면 사용자 저장도 같은 TX에서 되돌립니다.
        if (!intent.canComplete(user.getId(), clock.instant())) throw unavailable();
        intent.attached(clock.instant(), properties.recheckDelay());
        if (previousUrl != null && !previousUrl.equals(intent.getImageUrl())) {
            intents.findByUserIdAndImageUrl(user.getId(), previousUrl).ifPresent(previous -> {
                if (previous.ownsKey()) previous.orphaned(clock.instant());
            });
        }
        return intent.getImageUrl();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int queueDueCleanup() {
        Instant now = clock.instant();
        int queued = 0;
        for (ProfileImageUploadIntent intent : intents.findDueForUpdate(now, properties.batchSize())) {
            if (!intent.ownsKey()) throw new IllegalStateException("서버가 소유하지 않은 프로필 key입니다.");
            if (referenced(intent)) {
                intent.attached(now, properties.recheckDelay());
                continue;
            }
            if (intent.getState() == State.UPLOADING && intent.getUploadDeadline().isAfter(now)) continue;
            if (intent.getState() != State.ORPHAN) intent.orphaned(now);
            if (!intent.deletionDue(now)) continue;
            String attempt = intent.queueDeletion(now, properties.recheckDelay());
            publisher.publish("PROFILE_IMAGE_DELETE:" + intent.getId() + ":" + attempt,
                    OutboxEventType.PROFILE_IMAGE_DELETE_REQUESTED, new DeleteRequest(intent.getId(), attempt),
                    "PROFILE_IMAGE_UPLOAD", intent.getId());
            queued++;
        }
        return queued;
    }

    /** 실제 S3 삭제 직전에 새 트랜잭션으로 현재·공유 프로필 참조와 시도를 재확인합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String deletionKey(DeleteRequest request) {
        ProfileImageUploadIntent intent = intents.findForUpdate(request.intentId()).orElse(null);
        if (intent == null || !intent.matchesAttempt(request.attempt()) || !intent.ownsKey()) return null;
        if (referenced(intent)) {
            intent.attached(clock.instant(), properties.recheckDelay());
            return null;
        }
        return intent.deletionDue(clock.instant()) ? intent.getS3Key() : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recheckAfter(DeleteRequest request, Instant when) {
        intents.findForUpdate(request.intentId()).ifPresent(intent -> {
            if (intent.matchesAttempt(request.attempt())) intent.recheckAt(when);
        });
    }

    private boolean referenced(ProfileImageUploadIntent intent) {
        return users.existsByProfileImageUrl(intent.getImageUrl());
    }

    private static UsersException unavailable() { return new UsersException(UsersErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE); }

    public record PreparedUpload(String intentId, String key) {}
    public record DeleteRequest(String intentId, String attempt) {}
}
