package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.api.dto.MenuConvertedPriceResponse;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 메뉴 원금에 환율을 곱해 표시 통화 가격을 계산합니다.
 * 동일 통화 또는 환율 미확보는 null이며 KRW/JPY는 정수, USD/CNY/EUR는 소수 둘째 자리에서 HALF_UP 반올림합니다.
 */
@Service
@RequiredArgsConstructor
public class MenuPriceConversionService {

    private final CurrencyExchangeRateClient currencyExchangeRateClient;

    /**
     * 메뉴 원금과 조회한 환율로 표시 통화 금액을 계산해 적용 환율 날짜와 함께 반환합니다.
     * 동일 통화·환율 부재는 null이며 KRW·JPY는 정수, 나머지 지원 통화는 소수 둘째 자리로 HALF_UP 반올림합니다.
     */
    public MenuConvertedPriceResponse convert(PlaceMenu menu, MenuCurrency displayCurrency) {
        if (menu.getCurrency() == displayCurrency) {
            return null;
        }

        Optional<CurrencyExchangeRate> exchangeRate = currencyExchangeRateClient.findRate(
                menu.getCurrency(), displayCurrency
        );
        if (exchangeRate.isEmpty()) {
            return null;
        }

        CurrencyExchangeRate rate = exchangeRate.get();
        BigDecimal convertedAmount = BigDecimal.valueOf(menu.getPriceAmount())
                .multiply(rate.rate())
                .setScale(fractionDigits(displayCurrency), RoundingMode.HALF_UP);
        return new MenuConvertedPriceResponse(convertedAmount, displayCurrency, rate.rateDate());
    }

    private int fractionDigits(MenuCurrency currency) {
        return switch (currency) {
            case KRW, JPY -> 0;
            case USD, CNY, EUR -> 2;
        };
    }
}
