package com.typenull.pingdom.menu.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceMenuResponseTest {

    /**
     * 공개 응답에 환산 가격을 추가해도 점주·공개 응답의 원가격 9,000 KRW가 유지되고 환산 객체를 별도로 보유하는지 검증.
     */
    @Test
    void conversionPreservesOriginalMenuPrice() {
        PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW,
                null, 0, LocalDateTime.of(2026, 9, 12, 11, 0));
        MenuConvertedPriceResponse convertedPrice = new MenuConvertedPriceResponse(
                new BigDecimal("6.43"), MenuCurrency.USD, LocalDate.of(2026, 9, 10));

        PlaceMenuResponse ownedResponse = PlaceMenuResponse.from(menu);
        PlaceMenuPublicResponse publicResponse = PlaceMenuPublicResponse.from(menu, convertedPrice);

        assertThat(ownedResponse.priceAmount()).isEqualTo(9000L);
        assertThat(ownedResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(publicResponse.priceAmount()).isEqualTo(9000L);
        assertThat(publicResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(publicResponse.convertedPrice()).isEqualTo(convertedPrice);
    }
}
