package com.typenull.pingdom.engagement.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReporterModerationPolicyTest {

    /**
     * 승인 25회에도 신뢰도는 100을 넘지 않고 거절 6회에는 허위 신고 6회·신뢰도 0이 되는지 검증.
     */
    @Test
    void boundsRecalculatedTrustScore() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.create(1L, "reporter");

        for (int index = 0; index < 25; index++) {
            policy.recordAccepted("reporter");
        }

        assertThat(policy.getAcceptedCount()).isEqualTo(25L);
        assertThat(policy.getTrustScore()).isEqualTo(100);

        ReporterModerationPolicy lowScorePolicy = ReporterModerationPolicy.create(2L, "low-score-reporter");
        for (int index = 0; index < 6; index++) {
            lowScorePolicy.recordDeclined("low-score-reporter");
        }

        assertThat(lowScorePolicy.getFalseReportCount()).isEqualTo(6L);
        assertThat(lowScorePolicy.getTrustScore()).isEqualTo(0);
    }

    /**
     * 제한이 만료되면 만료 시각·사유를 제거하고 허위 신고 수를 0·신뢰도를 100으로 복원하는지 검증.
     */
    @Test
    void clearsExpiredReporterRestriction() {
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(1L)
                .reporterUsername("reporter")
                .acceptedCount(2L)
                .declinedCount(3L)
                .falseReportCount(3L)
                .trustScore(40)
                .restrictedUntil(LocalDateTime.of(2026, 7, 27, 11, 59))
                .restrictionReason("FALSE_REPORT_THRESHOLD_EXCEEDED")
                .build();

        policy.clearExpiredRestriction(LocalDateTime.of(2026, 7, 27, 12, 0));

        assertThat(policy.getRestrictedUntil()).isNull();
        assertThat(policy.getRestrictionReason()).isNull();
        assertThat(policy.getFalseReportCount()).isZero();
        assertThat(policy.getTrustScore()).isEqualTo(100);
    }

    /**
     * 제한이 아직 유효하면 제한 여부·허위 신고 수 3·신뢰도 40을 유지하는지 검증.
     */
    @Test
    void preservesActiveReporterRestriction() {
        LocalDateTime restrictedUntil = LocalDateTime.of(2026, 7, 28, 12, 0);
        ReporterModerationPolicy policy = ReporterModerationPolicy.builder()
                .reporterUserId(1L)
                .reporterUsername("reporter")
                .falseReportCount(3L)
                .trustScore(40)
                .restrictedUntil(restrictedUntil)
                .restrictionReason("FALSE_REPORT_THRESHOLD_EXCEEDED")
                .build();

        policy.clearExpiredRestriction(LocalDateTime.of(2026, 7, 27, 12, 0));

        assertThat(policy.isRestricted(LocalDateTime.of(2026, 7, 27, 12, 0))).isTrue();
        assertThat(policy.getFalseReportCount()).isEqualTo(3L);
        assertThat(policy.getTrustScore()).isEqualTo(40);
    }
}
