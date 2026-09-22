package com.typenull.pingdom.boost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VerifiedBoostProductTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    /**
     * 초안 상품을 활성화한 뒤 비활성화하면 INACTIVE 상태와 마지막 수정 시각을 기록하는지 검증한다.
     */
    @Test
    void activatesThenDeactivatesDraft() {
        VerifiedBoostProduct product = product();

        product.activate(NOW.plusMinutes(1));
        product.deactivate(NOW.plusMinutes(2));

        assertThat(product.getStatus()).isEqualTo(VerifiedBoostProductStatus.INACTIVE);
        assertThat(product.getUpdatedAt()).isEqualTo(NOW.plusMinutes(2));
    }

    /**
     * 한 번도 활성화되지 않은 초안을 비활성화하면 IllegalStateException이 발생하는지 검증한다.
     */
    @Test
    void draftCannotBeDeactivated() {
        VerifiedBoostProduct product = product();

        assertThatThrownBy(() -> product.deactivate(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 가격과 기간이 모두 0인 상품 초안 생성이 IllegalArgumentException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsZeroPriceAndDuration() {
        assertThatThrownBy(() -> VerifiedBoostProduct.draft("Boost", "description", 0, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 가격 30,000·기간 7일인 초안을 만들어 상태 전이 테스트의 유효한 초기 조건을 제공한다.
     */
    private VerifiedBoostProduct product() {
        return VerifiedBoostProduct.draft("Boost", "description", 30_000, 7, NOW);
    }
}
