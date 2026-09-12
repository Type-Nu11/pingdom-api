package com.typenull.pingdom.menu.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceMenuTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 11, 0);

    @Test
    void keepsOriginalPriceAndCurrencyAsMenuData() {
        PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW,
                null, 0, NOW);

        assertThat(menu.getPriceAmount()).isEqualTo(9000L);
        assertThat(menu.getCurrency()).isEqualTo(MenuCurrency.KRW);
    }

    @Test
    void rejectsZeroOrNegativeOriginalPrice() {
        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "무료 메뉴", null, 0L, MenuCurrency.KRW,
                null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("priceAmount must be positive");

        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "오류 메뉴", null, -1L, MenuCurrency.KRW,
                null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("priceAmount must be positive");
    }

    @Test
    void rejectsMissingOriginalCurrency() {
        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, null,
                null, 0, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency must not be null");
    }
}
