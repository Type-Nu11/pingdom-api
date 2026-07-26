package com.typenull.pingdom.boost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VerifiedBoostExecutionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Test
    void executionUsesProductDurationAndExpiresAtEndTime() {
        MerchantVerifiedBoostSelection selection = selection();

        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection, 7, NOW);

        assertThat(execution.isActiveAt(NOW.plusDays(6))).isTrue();
        assertThat(execution.isActiveAt(NOW.plusDays(7))).isFalse();
        assertThat(execution.effectiveStatusAt(NOW.plusDays(7)))
                .isEqualTo(VerifiedBoostExecutionStatus.EXPIRED);
    }

    @Test
    void expiredExecutionCannotBeStopped() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 1, NOW);

        assertThatThrownBy(() -> execution.stop(NOW.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private MerchantVerifiedBoostSelection selection() {
        MerchantVerifiedBoostSelection selection = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        ReflectionTestUtils.setField(selection, "id", 4L);
        return selection;
    }
}
