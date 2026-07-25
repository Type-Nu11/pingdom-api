package com.typenull.pingdom.boost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VerifiedBoostProductTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Test
    void draftCanBeActivatedAndDeactivated() {
        VerifiedBoostProduct product = product();

        product.activate(NOW.plusMinutes(1));
        product.deactivate(NOW.plusMinutes(2));

        assertThat(product.getStatus()).isEqualTo(VerifiedBoostProductStatus.INACTIVE);
        assertThat(product.getUpdatedAt()).isEqualTo(NOW.plusMinutes(2));
    }

    @Test
    void draftCannotBeDeactivated() {
        VerifiedBoostProduct product = product();

        assertThatThrownBy(() -> product.deactivate(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidPriceAndDurationAreRejected() {
        assertThatThrownBy(() -> VerifiedBoostProduct.draft(1L, 2L, "Boost", "description", 0, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private VerifiedBoostProduct product() {
        return VerifiedBoostProduct.draft(1L, 2L, "Boost", "description", 30_000, 7, NOW);
    }
}
