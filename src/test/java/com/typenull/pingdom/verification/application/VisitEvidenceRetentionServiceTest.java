package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.infrastructure.VisitEvidenceRepository;
import java.time.*;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class VisitEvidenceRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private final VisitEvidenceRepository repository = mock(VisitEvidenceRepository.class);
    private final S3ObjectDeleteOutboxPublisher publisher = mock(S3ObjectDeleteOutboxPublisher.class);
    private final VisitEvidenceRetentionService service = new VisitEvidenceRetentionService(repository,
            new VisitEvidenceProperties(Duration.ofDays(30), 1024L, 10, 10), publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    /**
     * 하루 전에 만료된 증빙 하나를 조회하도록 구성하고 삭제 건수 1을 확인한다.
     * S3 삭제 Outbox 발행에 key·유형·사유가 전달되고 DB 일괄 삭제가 호출되어야 한다.
     * 영속화하지 않은 fixture의 ID가 null이므로 발행 인자의 ID 문자열도 "null"이다.
     */
    @Test
    void purgeExpiredEvidence() {
        VisitEvidence evidence = VisitEvidence.create(2L, 1L, "visit-evidence/key", "visit.jpg",
                "image/jpeg", 4, NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)));
        when(repository.findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(evidence));

        int deleted = service.purgeExpiredEvidence();

        assertThat(deleted).isEqualTo(1);
        verify(publisher).publish("visit-evidence/key", "VISIT_EVIDENCE", "null",
                "VISIT_EVIDENCE_RETENTION_EXPIRED");
        verify(repository).deleteAllInBatch(List.of(evidence));
    }

    /**
     * 첫 조회에 25건, 다음 조회에 1건을 반환해 총 26건과 두 번의 DB 삭제를 확인한다.
     * 첫 mock 결과는 설정 배치 크기 10보다 크며, 실제 Pageable 제한 준수 자체를 검증하지 않는다.
     * 반환 건수가 배치 크기보다 작은 두 번째 조회에서 순회를 끝내는 흐름을 다룬다.
     */
    @Test
    void stopAfterPartialBatch() {
        List<VisitEvidence> fullBatch = IntStream.range(0, 25)
                .mapToObj(index -> evidence("visit-evidence/key-" + index))
                .toList();
        List<VisitEvidence> finalBatch = List.of(evidence("visit-evidence/final"));
        when(repository.findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(eq(NOW), any(Pageable.class)))
                .thenReturn(fullBatch, finalBatch);

        assertThat(service.purgeExpiredEvidence()).isEqualTo(26);
        verify(repository, times(2)).deleteAllInBatch(anyList());
    }

    /** 지정한 S3 key로 고정 시각보다 하루 전에 만료된 미영속 증빙을 만든다. */
    private VisitEvidence evidence(String key) {
        return VisitEvidence.create(2L, 1L, key, "visit.jpg", "image/jpeg", 4,
                NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)));
    }
}
