package com.typenull.pingdom.engagement.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReporterModerationPolicyTest {

    @Test
    void recalculatesTrustScoreWithinConfiguredBounds() {
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

    @Test
    void clearsExpiredRestrictionAndRecalculatesFromPersistedCounters() {
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

    @Test
    void retainsActiveRestrictionWithoutChangingCounters() {
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
