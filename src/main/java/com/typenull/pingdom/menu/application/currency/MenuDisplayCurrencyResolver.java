package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 등록된 국가 코드의 표시 통화를 정하고 null·공백·미지원 코드는 KRW로 반환.
 * 국가 코드 전체 표준을 해석하지 않고 이 클래스에 명시된 코드만 지원.
 */
@Component
public class MenuDisplayCurrencyResolver {

    private static final Map<String, MenuCurrency> COUNTRY_CURRENCIES = Map.ofEntries(
            Map.entry("KR", MenuCurrency.KRW),
            Map.entry("KOR", MenuCurrency.KRW),
            Map.entry("US", MenuCurrency.USD),
            Map.entry("USA", MenuCurrency.USD),
            Map.entry("JP", MenuCurrency.JPY),
            Map.entry("JPN", MenuCurrency.JPY),
            Map.entry("CN", MenuCurrency.CNY),
            Map.entry("CHN", MenuCurrency.CNY),
            Map.entry("AT", MenuCurrency.EUR),
            Map.entry("BE", MenuCurrency.EUR),
            Map.entry("CY", MenuCurrency.EUR),
            Map.entry("DE", MenuCurrency.EUR),
            Map.entry("ES", MenuCurrency.EUR),
            Map.entry("EE", MenuCurrency.EUR),
            Map.entry("FI", MenuCurrency.EUR),
            Map.entry("FR", MenuCurrency.EUR),
            Map.entry("GR", MenuCurrency.EUR),
            Map.entry("HR", MenuCurrency.EUR),
            Map.entry("IE", MenuCurrency.EUR),
            Map.entry("IT", MenuCurrency.EUR),
            Map.entry("LT", MenuCurrency.EUR),
            Map.entry("LU", MenuCurrency.EUR),
            Map.entry("LV", MenuCurrency.EUR),
            Map.entry("MT", MenuCurrency.EUR),
            Map.entry("NL", MenuCurrency.EUR),
            Map.entry("PT", MenuCurrency.EUR),
            Map.entry("SI", MenuCurrency.EUR),
            Map.entry("SK", MenuCurrency.EUR)
    );

    public MenuCurrency resolve(String country) {
        if (country == null || country.isBlank()) {
            return MenuCurrency.KRW;
        }
        return COUNTRY_CURRENCIES.getOrDefault(country.trim().toUpperCase(Locale.ROOT), MenuCurrency.KRW);
    }
}
