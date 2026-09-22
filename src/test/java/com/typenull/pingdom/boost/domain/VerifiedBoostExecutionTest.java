package com.typenull.pingdom.boost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VerifiedBoostExecutionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    /**
     * 7일 상품의 실행이 6일 뒤에는 활성이고 종료 시각에는 비활성·EXPIRED인지 검증해 만료 경계를 고정.
     */
    @Test
    void expiresAtProductDuration() {
        MerchantVerifiedBoostSelection selection = selection();

        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection, 7, NOW);

        assertThat(execution.isActiveAt(NOW.plusDays(6))).isTrue();
        assertThat(execution.isActiveAt(NOW.plusDays(7))).isFalse();
        assertThat(execution.effectiveStatusAt(NOW.plusDays(7)))
                .isEqualTo(VerifiedBoostExecutionStatus.EXPIRED);
    }

    /**
     * 하루 실행의 종료 시각에 중단을 시도하면 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void expiredExecutionCannotBeStopped() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 1, NOW);

        assertThatThrownBy(() -> execution.stop(NOW.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 실행 생성에 필요한 상품 선택 엔티티를 만들고 DB 저장 이후를 재현하도록 ID를 지정.
     */
    private MerchantVerifiedBoostSelection selection() {
        MerchantVerifiedBoostSelection selection = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        ReflectionTestUtils.setField(selection, "id", 4L);
        return selection;
    }
}
