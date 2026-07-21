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

    @Test
    void publishesS3DeletionAndRemovesExpiredRowsInConfiguredBatch() {
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

    @Test
    void continuesUntilPartialBatchIsProcessed() {
        List<VisitEvidence> fullBatch = IntStream.range(0, 25)
                .mapToObj(index -> evidence("visit-evidence/key-" + index))
                .toList();
        List<VisitEvidence> finalBatch = List.of(evidence("visit-evidence/final"));
        when(repository.findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(eq(NOW), any(Pageable.class)))
                .thenReturn(fullBatch, finalBatch);

        assertThat(service.purgeExpiredEvidence()).isEqualTo(26);
        verify(repository, times(2)).deleteAllInBatch(anyList());
    }

    private VisitEvidence evidence(String key) {
        return VisitEvidence.create(2L, 1L, key, "visit.jpg", "image/jpeg", 4,
                NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)));
    }
}
