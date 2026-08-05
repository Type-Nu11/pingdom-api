package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutActivityEligibilityTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime ELIGIBLE_FROM = LocalDateTime.of(2026, 8, 2, 9, 0);
    private static final LocalDateTime ELIGIBLE_UNTIL = LocalDateTime.of(2026, 9, 2, 9, 0);

    @Test
    void eligibilityIsAvailableOnlyInsideTheConfiguredPeriod() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_UNTIL, ELIGIBLE_FROM);

        assertThat(eligibility.isEligibleAt(ELIGIBLE_FROM.minusNanos(1))).isFalse();
        assertThat(eligibility.isEligibleAt(ELIGIBLE_FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(ELIGIBLE_UNTIL)).isFalse();
    }

    @Test
    void eligibilityRejectsAZeroLengthPeriod() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_FROM, ELIGIBLE_FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredEligibilityCannotBeUsed() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_UNTIL, ELIGIBLE_FROM);

        eligibility.expire(ELIGIBLE_UNTIL);

        assertThat(eligibility.getStatus()).isEqualTo(ScoutActivityEligibilityStatus.EXPIRED);
        assertThat(eligibility.isEligibleAt(ELIGIBLE_UNTIL.plusNanos(1))).isFalse();
    }
}
