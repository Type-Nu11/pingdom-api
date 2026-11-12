package com.typenull.pingdom.moderation.application.service.trust;

import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreBatchResponse;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryItem;
import com.typenull.pingdom.moderation.api.dto.trust.AdminTrustScoreChangeHistoryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ID 목록만 제한된 크기로 읽고 정책별 트랜잭션에서 최신 통계로 점수를 재계산.
 * 점수가 바뀐 정책에만 변경 이력을 저장하며 만료 제한 해제·이상 징후 탐지·개입 규칙 평가는 처리 범위에서 제외.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrustScoreBatchService {

    private static final int ID_PAGE_SIZE = 100;

    private final ReporterModerationPolicyRepository policyRepository;
    private final TrustScoreChangeHistoryRepository historyRepository;
    private final TrustScoreRecalculationService recalculationService;

    /**
     * 실행 시작 시 최대 ID를 상한으로 순회하며 실제 커밋한 정책 수와 점수 변경 수를 반환.
     * 중간 실패 전 행은 유지되며 재실행 시 동일 점수에는 이력을 추가하지 않습니다.
     * 순회 도중 생성된 상한 이하 정책은 커서가 아직 지나지 않았다면 포함될 수 있습니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminTrustScoreBatchResponse recalculate() {
        Long throughId = policyRepository.findMaxReporterUserId();
        if (throughId == null) {
            return new AdminTrustScoreBatchResponse(0, 0);
        }
        Long afterId = null;
        int processed = 0;
        int changed = 0;
        while (true) {
            List<Long> reporterIds = policyRepository.findReporterUserIdsAfter(
                    afterId, throughId, PageRequest.of(0, ID_PAGE_SIZE));
            if (reporterIds.isEmpty()) {
                break;
            }
            for (Long reporterId : reporterIds) {
                TrustScoreRecalculationService.RecalculationResult result;
                try {
                    result = recalculationService.recalculate(reporterId);
                } catch (RuntimeException exception) {
                    log.warn("Trust score batch stopped: reporterId={}, processed={}, changed={}",
                            reporterId, processed, changed, exception);
                    throw exception;
                }
                if (result.processed()) {
                    processed++;
                }
                if (result.changed()) {
                    changed++;
                }
            }
            afterId = reporterIds.get(reporterIds.size() - 1);
        }
        return new AdminTrustScoreBatchResponse(processed, changed);
    }

    /**
     * 해당 신고자의 점수 변경 이력을 변경 시각·ID 내림차순으로 조회. page는 1 이상·limit는 1~100으로 보정.
     */
    @Transactional(readOnly = true)
    public AdminTrustScoreChangeHistoryResponse listHistory(Long reporterUserId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var result = historyRepository.findAllByReporterUserId(reporterUserId,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("changedAt").descending().and(Sort.by("id").descending())));
        return new AdminTrustScoreChangeHistoryResponse(
                result.getContent().stream().map(AdminTrustScoreChangeHistoryItem::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
}
