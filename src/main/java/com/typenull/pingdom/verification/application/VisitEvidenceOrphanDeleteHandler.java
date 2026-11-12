package com.typenull.pingdom.verification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.verification.application.VisitEvidenceUploadIntentService.DeleteRequest;
import com.typenull.pingdom.verification.domain.VisitEvidenceUploadIntent;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 활성 참조를 재검증하고 S3 작업은 DB 트랜잭션 밖에서 실행합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceOrphanDeleteHandler implements OutboxEventHandler {
    private final VisitEvidenceUploadIntentService intents;
    private final S3ObjectStorage storage;
    private final ObjectMapper mapper;
    private final VisitEvidenceOrphanProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;

    @Override public OutboxEventType supportedType() { return OutboxEventType.VISIT_EVIDENCE_ORPHAN_DELETE_REQUESTED; }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void handle(String eventId, String payload) {
        DeleteRequest request;
        try { request = mapper.readValue(payload, DeleteRequest.class); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("증빙 orphan 삭제 payload가 올바르지 않습니다.", exception); }
        if (request == null || request.intentId() == null || request.attempt() == null)
            throw new IllegalArgumentException("증빙 orphan 삭제 시도 식별자가 없습니다.");
        String key = intents.deletionKey(request);
        if (key == null) return;
        var object = storage.findObjectSnapshot(key);
        if (object.isPresent()) {
            var earliestDelete = object.get().lastModified().plus(VisitEvidenceUploadIntent.ORPHAN_RETENTION);
            if (earliestDelete.isAfter(clock.instant())) {
                intents.recheckAfter(request, earliestDelete);
                return;
            }
            if (!object.get().hasImmutableVersion()) {
                metrics.counter("visit.evidence.orphan.deferred", "reason", "missing_immutable_version").increment();
                log.warn("증빙 orphan 삭제를 보류합니다. immutable versionId가 없습니다. intentId={}", request.intentId());
                intents.recheckAfter(request, clock.instant().plus(properties.recheckDelay()));
                return;
            }
            // HEAD 뒤 참조가 생기거나 시도가 바뀌었으면 S3 삭제를 시작하지 않습니다.
            if (intents.deletionKey(request) == null) return;
            storage.deleteIfUnchanged(key, object.get());
        }
        // 삭제 성공/부재 뒤에도 이력을 지우지 않아 늦은 put과 객체 재발생을 재확인합니다.
        intents.recheckAfter(request, clock.instant().plus(properties.recheckDelay()));
    }
}
