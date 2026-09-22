package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.api.dto.MenuConvertedPriceResponse;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 메뉴 원금에 환율을 곱해 표시 통화 가격을 계산.
 * 동일 통화 또는 환율 미확보는 null이며 KRW/JPY는 정수, USD/CNY/EUR는 소수 둘째 자리에서 HALF_UP 반올림.
 */
@Service
@RequiredArgsConstructor
public class MenuPriceConversionService {

    private final CurrencyExchangeRateClient currencyExchangeRateClient;

    /**
     * 메뉴 원금과 조회한 환율로 표시 통화 금액을 계산해 적용 환율 날짜와 함께 반환.
     * 동일 통화·환율 부재는 null이며 KRW·JPY는 정수, 나머지 지원 통화는 소수 둘째 자리로 HALF_UP 반올림.
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

        return convert(menu, displayCurrency, exchangeRate.get());
    }

    /**
     * 하나의 메뉴 목록 응답 안에서 통화 쌍별 환율 조회 결과를 재사용.
     * 실패한 빈 결과도 목록 생성 동안만 보관해 장애 시 순차 외부 호출을 막고, 다음 HTTP 요청에서는 다시 조회함.
     */
    public List<MenuConvertedPriceResponse> convertAll(List<PlaceMenu> menus, MenuCurrency displayCurrency) {
        Map<CurrencyPair, Optional<CurrencyExchangeRate>> rates = new HashMap<>();
        List<MenuConvertedPriceResponse> convertedPrices = new ArrayList<>(menus.size());
        for (PlaceMenu menu : menus) {
            if (menu.getCurrency() == displayCurrency) {
                convertedPrices.add(null);
                continue;
            }
            CurrencyPair pair = new CurrencyPair(menu.getCurrency(), displayCurrency);
            Optional<CurrencyExchangeRate> rate = rates.computeIfAbsent(pair,
                    ignored -> currencyExchangeRateClient.findRate(pair.sourceCurrency(), pair.targetCurrency()));
            convertedPrices.add(rate.map(value -> convert(menu, displayCurrency, value)).orElse(null));
        }
        return convertedPrices;
    }

    private MenuConvertedPriceResponse convert(PlaceMenu menu, MenuCurrency displayCurrency,
                                               CurrencyExchangeRate rate) {
        BigDecimal convertedAmount = BigDecimal.valueOf(menu.getPriceAmount())
                .multiply(rate.rate())
                .setScale(fractionDigits(displayCurrency), RoundingMode.HALF_UP);
        return new MenuConvertedPriceResponse(convertedAmount, displayCurrency, rate.rateDate());
    }

    private record CurrencyPair(MenuCurrency sourceCurrency, MenuCurrency targetCurrency) {
    }

    private int fractionDigits(MenuCurrency currency) {
        return switch (currency) {
            case KRW, JPY -> 0;
            case USD, CNY, EUR -> 2;
        };
    }
}
