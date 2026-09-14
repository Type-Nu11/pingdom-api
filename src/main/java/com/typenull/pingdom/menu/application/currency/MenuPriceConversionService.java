package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.api.dto.MenuConvertedPriceResponse;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MenuPriceConversionService {

    private final CurrencyExchangeRateClient currencyExchangeRateClient;

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
