package com.typenull.pingdom.identity.application.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.command.ProfileImageUploadService.DeleteRequest;
import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** DB 참조를 재검증한 뒤 관측한 immutable S3 버전만 삭제합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileImageDeleteHandler implements OutboxEventHandler {
    private final ProfileImageUploadService images;
    private final S3ObjectStorage storage;
    private final ObjectMapper mapper;
    private final ProfileImageStorageProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;

    @Override public OutboxEventType supportedType() { return OutboxEventType.PROFILE_IMAGE_DELETE_REQUESTED; }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void handle(String eventId, String payload) {
        DeleteRequest request;
        try { request = mapper.readValue(payload, DeleteRequest.class); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("프로필 이미지 삭제 payload가 올바르지 않습니다.", exception); }
        if (request == null || request.intentId() == null || request.attempt() == null)
            throw new IllegalArgumentException("프로필 이미지 삭제 시도 식별자가 없습니다.");
        String key = images.deletionKey(request);
        if (key == null) return;
        var object = storage.findObjectSnapshot(key);
        if (object.isPresent()) {
            var earliestDelete = object.get().lastModified().plus(ProfileImageUploadIntent.ORPHAN_RETENTION);
            if (earliestDelete.isAfter(clock.instant())) {
                images.recheckAfter(request, earliestDelete);
                return;
            }
            if (!object.get().hasImmutableVersion()) {
                metrics.counter("profile.image.cleanup.deferred", "reason", "missing_immutable_version").increment();
                log.warn("프로필 이미지 삭제를 보류합니다. immutable versionId가 없습니다. intentId={}", request.intentId());
                images.recheckAfter(request, clock.instant().plus(properties.recheckDelay()));
                return;
            }
            if (images.deletionKey(request) == null) return;
            storage.deleteIfUnchanged(key, object.get());
        }
        // 부재/삭제 뒤에도 원장을 보존해 늦은 업로드와 객체 재발생을 다시 확인합니다.
        images.recheckAfter(request, clock.instant().plus(properties.recheckDelay()));
    }
}
