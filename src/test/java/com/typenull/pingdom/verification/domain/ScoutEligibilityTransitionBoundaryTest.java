package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutEligibilityTransitionBoundaryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime FROM = CREATED_AT.plusDays(1);
    private static final LocalDateTime UNTIL = CREATED_AT.plusDays(10);

    @Test
    void pendingEligibilityIsNotUsableBeforeGrant() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThat(eligibility.isEligibleAt(FROM)).isFalse();
    }

    @Test
    void eligibilityWindowIncludesStartAndExcludesEnd() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, UNTIL, FROM);

        assertThat(eligibility.isEligibleAt(FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(UNTIL.minusNanos(1))).isTrue();
        assertThat(eligibility.isEligibleAt(UNTIL)).isFalse();
    }

    @Test
    void nullEndTimeCreatesAnOpenEndedEligibilityWindow() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, null, FROM);

        assertThat(eligibility.isEligibleAt(FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(FROM.plusYears(1))).isTrue();
    }

    @Test
    void eligibilityCannotExpireBeforeItsEndTime() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, UNTIL, FROM);

        assertThatThrownBy(() -> eligibility.expire(UNTIL.minusNanos(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(eligibility.getStatus()).isEqualTo(ScoutActivityEligibilityStatus.ELIGIBLE);
    }

    @Test
    void pendingEligibilityCannotBeSuspendedBeforeGrant() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.suspend(99L, "심사 보류", FROM))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingEligibilityCannotBeRevokedBeforeGrant() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.revoke(99L, "자격 회수", FROM))
                .isInstanceOf(IllegalStateException.class);
    }
}
