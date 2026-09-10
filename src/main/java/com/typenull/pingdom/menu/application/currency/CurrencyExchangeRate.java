package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record CurrencyExchangeRate(
        MenuCurrency sourceCurrency,
        MenuCurrency targetCurrency,
        BigDecimal rate,
        LocalDate rateDate
) {
    public CurrencyExchangeRate {
        Objects.requireNonNull(sourceCurrency, "sourceCurrency must not be null");
        Objects.requireNonNull(targetCurrency, "targetCurrency must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(rateDate, "rateDate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
    }
}
