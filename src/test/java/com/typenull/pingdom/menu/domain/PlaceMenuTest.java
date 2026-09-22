package com.typenull.pingdom.menu.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceMenuTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 11, 0);

    /**
     * 메뉴를 생성하면 원가격 9,000과 KRW 통화를 그대로 보유하는지 검증한다.
     */
    @Test
    void preservesOriginalPriceAndCurrency() {
        PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW,
                null, 0, NOW);

        assertThat(menu.getPriceAmount()).isEqualTo(9000L);
        assertThat(menu.getCurrency()).isEqualTo(MenuCurrency.KRW);
    }

    /**
     * 가격 0과 -1이 모두 양수 가격을 요구하는 IllegalArgumentException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsNonpositiveOriginalPrice() {
        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "무료 메뉴", null, 0L, MenuCurrency.KRW,
                null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("priceAmount must be positive");

        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "오류 메뉴", null, -1L, MenuCurrency.KRW,
                null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("priceAmount must be positive");
    }

    /**
     * 원통화가 null이면 currency 누락을 설명하는 NullPointerException이 발생하는지 검증한다.
     */
    @Test
    void rejectsMissingOriginalCurrency() {
        assertThatThrownBy(() -> PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, null,
                null, 0, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency must not be null");
    }
}
