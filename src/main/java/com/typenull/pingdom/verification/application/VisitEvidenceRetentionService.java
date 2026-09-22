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

/**
 * 만료 증빙의 S3 삭제 요청을 Outbox에 남기고 DB 증빙 행을 정리한다.
 * S3 실삭제는 별도 소비자가 수행하므로 메서드 반환 시 객체 삭제까지 끝났다는 의미는 아니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceRetentionService {
    private final VisitEvidenceRepository evidenceRepository;
    private final VisitEvidenceProperties properties;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final Clock clock;

    /**
     * 만료 시각·ID 순으로 제한된 배치를 반복 삭제하고 제거한 DB 행 수를 반환한다.
     * 전체 반복은 하나의 DB 트랜잭션이며 배치마다 독립 커밋하지 않는다.
     * 빈 결과·부분 배치·최대 반복 수 중 하나에 도달하면 종료한다.
     */
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
            // 삭제 요청을 먼저 기록해 DB 메타데이터 제거 후에도 S3 key를 Outbox에 남긴다.
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
