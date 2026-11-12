package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.VisitEvidenceUploadIntent;
import com.typenull.pingdom.verification.domain.VisitEvidenceUploadIntent.State;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** intent와 증빙 연결·정리 판정만 짧게 커밋하며 외부 S3 호출은 담당하지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class VisitEvidenceUploadIntentService {
    private final VisitEvidenceUploadIntentRepository intents;
    private final VisitEvidenceRepository evidence;
    private final VisitEvidencePersistenceService persistence;
    private final OutboxEventPublisher publisher;
    private final VisitEvidenceOrphanProperties properties;
    private final Clock clock;

    public PreparedUpload prepare(Long userId, Long checkInId, String extension) {
        persistence.requireOwnedCheckIn(userId, checkInId);
        VisitEvidenceUploadIntent intent = intents.saveAndFlush(VisitEvidenceUploadIntent.create(
                userId, checkInId, extension, clock.instant(), properties.uploadTimeout()));
        return new PreparedUpload(intent.getId(), intent.getS3Key());
    }

    public VisitEvidence complete(String id, Long userId, Long checkInId, String filename,
            String contentType, long size, Instant createdAt, Instant expiresAt) {
        VisitEvidenceUploadIntent intent = intents.findForUpdate(id).orElseThrow(VisitEvidenceUploadIntentService::unavailable);
        if (!intent.canComplete(userId, checkInId, clock.instant())) throw unavailable();
        VisitEvidence saved = persistence.save(userId, checkInId, intent.getS3Key(), filename,
                contentType, size, createdAt, expiresAt);
        // 저장 잠금 대기 동안 만료됐다면 같은 트랜잭션의 증빙 저장도 롤백합니다.
        if (!intent.canComplete(userId, checkInId, clock.instant())) throw unavailable();
        intent.attached(clock.instant(), properties.recheckDelay());
        return saved;
    }

    /** 커밋 결과가 불명확한 실패에서도 실제 증빙 참조가 있으면 현재 파일을 보존합니다. */
    public void abandon(String id) {
        intents.findForUpdate(id).ifPresent(intent -> {
            if (evidence.existsByS3Key(intent.getS3Key())) intent.attached(clock.instant(), properties.recheckDelay());
            else intent.orphaned(clock.instant());
        });
    }

    public int queueDueCleanup() {
        Instant now = clock.instant();
        int queued = 0;
        for (VisitEvidenceUploadIntent intent : intents.findDueForUpdate(now, properties.batchSize())) {
            if (!intent.ownsKey()) throw new IllegalStateException("서버가 소유하지 않은 증빙 key입니다.");
            if (evidence.existsByS3Key(intent.getS3Key())) {
                intent.attached(now, properties.recheckDelay());
                continue;
            }
            if (intent.getState() == State.UPLOADING && intent.getUploadDeadline().isAfter(now)) continue;
            if (intent.getState() != State.ORPHAN) intent.orphaned(now);
            if (!intent.deletionDue(now)) continue;
            String attempt = intent.queueDeletion(now, properties.recheckDelay());
            publisher.publish("VISIT_EVIDENCE_ORPHAN_DELETE:" + intent.getId() + ":" + attempt,
                    OutboxEventType.VISIT_EVIDENCE_ORPHAN_DELETE_REQUESTED,
                    new DeleteRequest(intent.getId(), attempt), "VISIT_EVIDENCE_UPLOAD", intent.getId());
            queued++;
        }
        return queued;
    }

    /** Outbox가 지연·중복 실행되어도 삭제 직전 실제 참조와 현재 시도를 다시 확인합니다. */
    public String deletionKey(DeleteRequest request) {
        VisitEvidenceUploadIntent intent = intents.findForUpdate(request.intentId()).orElse(null);
        if (intent == null || !intent.matchesAttempt(request.attempt()) || !intent.ownsKey()) return null;
        if (evidence.existsByS3Key(intent.getS3Key())) {
            intent.attached(clock.instant(), properties.recheckDelay());
            return null;
        }
        return intent.deletionDue(clock.instant()) ? intent.getS3Key() : null;
    }

    public void recheckAfter(DeleteRequest request, Instant when) {
        intents.findForUpdate(request.intentId()).ifPresent(intent -> {
            if (intent.matchesAttempt(request.attempt())) intent.recheckAt(when);
        });
    }

    private static VisitorVerificationException unavailable() {
        return new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
    }

    public record PreparedUpload(String intentId, String key) {}
    public record DeleteRequest(String intentId, String attempt) {}
}
