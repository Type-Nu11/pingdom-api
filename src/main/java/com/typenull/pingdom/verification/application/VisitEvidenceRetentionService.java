package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.infrastructure.VisitEvidenceRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceRetentionService {
    private final VisitEvidenceRepository evidenceRepository;
    private final VisitEvidenceProperties properties;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final Clock clock;

    @Transactional
    public int purgeExpiredEvidence() {
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.maxCleanupBatches(); batch++) {
            List<VisitEvidence> expired = evidenceRepository.findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                    clock.instant(), PageRequest.of(0, properties.cleanupBatchSize()));
            if (expired.isEmpty()) break;
            for (VisitEvidence evidence : expired) {
                deletePublisher.publish(evidence.getS3Key(), "VISIT_EVIDENCE", String.valueOf(evidence.getId()),
                        "VISIT_EVIDENCE_RETENTION_EXPIRED");
            }
            evidenceRepository.deleteAllInBatch(expired);
            totalDeleted += expired.size();
            if (expired.size() < properties.cleanupBatchSize()) break;
        }
        if (totalDeleted > 0) {
            log.info("보관 기간이 만료된 방문 인증 증빙을 삭제 요청했습니다. deletedCount={}", totalDeleted);
        }
        return totalDeleted;
    }
}
