package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ScoutEligibilityTransitionBoundaryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime FROM = CREATED_AT.plusDays(1);
    private static final LocalDateTime UNTIL = CREATED_AT.plusDays(10);

    /** 부여 전 PENDING 자격의 예정 시각 도달 후에도 활동 불가 판정 확인. */
    @Test
    void rejectPendingEligibility() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThat(eligibility.isEligibleAt(FROM)).isFalse();
    }

    /** 시작 시각과 종료 1ns 전은 유효하고 종료 시각은 제외되는 반개구간을 확인. */
    @Test
    void checkPeriodBoundaries() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, UNTIL, FROM);

        assertThat(eligibility.isEligibleAt(FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(UNTIL.minusNanos(1))).isTrue();
        assertThat(eligibility.isEligibleAt(UNTIL)).isFalse();
    }

    /** 종료 시각이 null이면 시작 시각뿐 아니라 1년 뒤에도 활동 가능해야 함. */
    @Test
    void allowOpenEndedEligibility() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, null, FROM);

        assertThat(eligibility.isEligibleAt(FROM)).isTrue();
        assertThat(eligibility.isEligibleAt(FROM.plusYears(1))).isTrue();
    }

    /** 종료 1ns 전의 만료 요청은 상태 오류로 거부하고 ELIGIBLE 상태를 유지해야 함. */
    @Test
    void rejectPrematureExpiry() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);
        eligibility.grant(99L, FROM, UNTIL, FROM);

        assertThatThrownBy(() -> eligibility.expire(UNTIL.minusNanos(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(eligibility.getStatus()).isEqualTo(ScoutActivityEligibilityStatus.ELIGIBLE);
    }

    /** 부여 전 PENDING 자격은 정지할 수 없어 상태 전이 오류를 반환. */
    @Test
    void rejectPendingSuspension() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.suspend(99L, "심사 보류", FROM))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 부여 전 PENDING 자격을 회수하면 상태 전이 오류로 거부. */
    @Test
    void rejectPendingRevocation() {
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(10L, CREATED_AT);

        assertThatThrownBy(() -> eligibility.revoke(99L, "자격 회수", FROM))
                .isInstanceOf(IllegalStateException.class);
    }
}
