package com.typenull.pingdom.menu.application.currency;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import org.junit.jupiter.api.Test;

class MenuDisplayCurrencyResolverTest {

    private final MenuDisplayCurrencyResolver resolver = new MenuDisplayCurrencyResolver();

    /**
     * 소문자 kr·US·3자리 JPN·CN·DE를 각각 KRW·USD·JPY·CNY·EUR로 해석하는지 검증한다.
     */
    @Test
    void resolvesSupportedCountryCurrencies() {
        assertThat(resolver.resolve("kr")).isEqualTo(MenuCurrency.KRW);
        assertThat(resolver.resolve("US")).isEqualTo(MenuCurrency.USD);
        assertThat(resolver.resolve("JPN")).isEqualTo(MenuCurrency.JPY);
        assertThat(resolver.resolve("CN")).isEqualTo(MenuCurrency.CNY);
        assertThat(resolver.resolve("DE")).isEqualTo(MenuCurrency.EUR);
    }

    /**
     * 국가가 null이거나 UNKNOWN이면 KRW를 반환해 기본 표시 통화를 유지하는지 검증한다.
     */
    @Test
    void defaultsUnknownCountryToKrw() {
        assertThat(resolver.resolve(null)).isEqualTo(MenuCurrency.KRW);
        assertThat(resolver.resolve("UNKNOWN")).isEqualTo(MenuCurrency.KRW);
    }
}
