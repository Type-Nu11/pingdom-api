package com.typenull.pingdom.menu.application.currency;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import org.junit.jupiter.api.Test;

class MenuDisplayCurrencyResolverTest {

    private final MenuDisplayCurrencyResolver resolver = new MenuDisplayCurrencyResolver();

    @Test
    void resolvesSupportedCountryCodesToDisplayCurrencies() {
        assertThat(resolver.resolve("kr")).isEqualTo(MenuCurrency.KRW);
        assertThat(resolver.resolve("US")).isEqualTo(MenuCurrency.USD);
        assertThat(resolver.resolve("JPN")).isEqualTo(MenuCurrency.JPY);
        assertThat(resolver.resolve("CN")).isEqualTo(MenuCurrency.CNY);
        assertThat(resolver.resolve("DE")).isEqualTo(MenuCurrency.EUR);
    }

    @Test
    void fallsBackToKrwForUnknownOrMissingCountry() {
        assertThat(resolver.resolve(null)).isEqualTo(MenuCurrency.KRW);
        assertThat(resolver.resolve("UNKNOWN")).isEqualTo(MenuCurrency.KRW);
    }
}
