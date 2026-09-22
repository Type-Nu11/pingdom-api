package com.typenull.pingdom.menu.application.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.menu.api.dto.MenuConvertedPriceResponse;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MenuPriceConversionServiceTest {

    private final CurrencyExchangeRateClient exchangeRateClient = mock(CurrencyExchangeRateClient.class);
    private final MenuPriceConversionService service = new MenuPriceConversionService(exchangeRateClient);

    /**
     * 9,000 KRW에 환율 0.000714를 적용해 USD 6.43으로 반올림하고 환율 기준일을 응답에 포함하는지 검증한다.
     */
    @Test
    void convertsUsingRateAndScale() {
        PlaceMenu menu = menu(MenuCurrency.KRW, 9000L);
        when(exchangeRateClient.findRate(MenuCurrency.KRW, MenuCurrency.USD)).thenReturn(Optional.of(
                new CurrencyExchangeRate(MenuCurrency.KRW, MenuCurrency.USD, new BigDecimal("0.000714"),
                        LocalDate.of(2026, 9, 10))
        ));

        MenuConvertedPriceResponse response = service.convert(menu, MenuCurrency.USD);

        assertThat(response.amount()).isEqualByComparingTo("6.43");
        assertThat(response.currency()).isEqualTo(MenuCurrency.USD);
        assertThat(response.rateDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    /**
     * 원통화와 표시 통화가 같으면 환산 결과를 생략하고 외부 환율 조회를 호출하지 않는지 검증한다.
     */
    @Test
    void skipsSameCurrencyConversion() {
        assertThat(service.convert(menu(MenuCurrency.KRW, 9000L), MenuCurrency.KRW)).isNull();
        verifyNoInteractions(exchangeRateClient);
    }

    /**
     * 환율 조회 결과가 없으면 null을 반환해 환산 가격만 생략하는지 검증한다.
     */
    @Test
    void omitsUnavailableRateConversion() {
        when(exchangeRateClient.findRate(MenuCurrency.KRW, MenuCurrency.USD)).thenReturn(Optional.empty());

        assertThat(service.convert(menu(MenuCurrency.KRW, 9000L), MenuCurrency.USD)).isNull();
    }

    /**
     * 주어진 원통화·가격으로 고정 시각의 메뉴를 만들어 환율 계산의 입력을 제공한다.
     */
    private PlaceMenu menu(MenuCurrency currency, long amount) {
        return PlaceMenu.create(10L, 7L, "짜장면", null, amount, currency, null, 0,
                LocalDateTime.of(2026, 9, 10, 0, 0));
    }
}
