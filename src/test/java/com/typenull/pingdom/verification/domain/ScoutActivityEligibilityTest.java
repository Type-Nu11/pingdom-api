package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutActivityEligibilityTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime ELIGIBLE_FROM = LocalDateTime.of(2026, 8, 2, 9, 0);
    private static final LocalDateTime ELIGIBLE_UNTIL = LocalDateTime.of(2026, 9, 2, 9, 0);

    /** 활동 자격 시작 1ns 전은 거부하고 시작 시각은 허용하되 종료 시각은 제외한다. */
    @Test
    void checkEligibilityPeriod() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_UNTIL, ELIGIBLE_FROM);

        assertThat(eligibility.isEligibleAt(ELIGIBLE_FROM.minusNanos(1))).isFalse();
        assertThat(eligibility.isEligibleAt(ELIGIBLE_FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(ELIGIBLE_UNTIL)).isFalse();
    }

    /** 시작과 종료 시각이 같으면 활동 자격 부여를 인자 오류로 거부한다. */
    @Test
    void rejectEmptyEligibilityPeriod() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_FROM, ELIGIBLE_FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 종료 시각에 만료 처리하면 EXPIRED 상태가 되고 1ns 뒤 활동 자격도 인정되지 않아야 한다. */
    @Test
    void rejectExpiredEligibility() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, ELIGIBLE_FROM, ELIGIBLE_UNTIL, ELIGIBLE_FROM);

        eligibility.expire(ELIGIBLE_UNTIL);

        assertThat(eligibility.getStatus()).isEqualTo(ScoutActivityEligibilityStatus.EXPIRED);
        assertThat(eligibility.isEligibleAt(ELIGIBLE_UNTIL.plusNanos(1))).isFalse();
    }
}
