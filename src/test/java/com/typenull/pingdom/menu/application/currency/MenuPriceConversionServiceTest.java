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

    @Test
    void convertsMenuPriceUsingExchangeRateAndCurrencyScale() {
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

    @Test
    void omitsConvertedPriceWithoutExternalRequestForSameCurrency() {
        assertThat(service.convert(menu(MenuCurrency.KRW, 9000L), MenuCurrency.KRW)).isNull();
        verifyNoInteractions(exchangeRateClient);
    }

    @Test
    void omitsConvertedPriceWhenExchangeRateIsUnavailable() {
        when(exchangeRateClient.findRate(MenuCurrency.KRW, MenuCurrency.USD)).thenReturn(Optional.empty());

        assertThat(service.convert(menu(MenuCurrency.KRW, 9000L), MenuCurrency.USD)).isNull();
    }

    private PlaceMenu menu(MenuCurrency currency, long amount) {
        return PlaceMenu.create(10L, 7L, "짜장면", null, amount, currency, null, 0,
                LocalDateTime.of(2026, 9, 10, 0, 0));
    }
}
