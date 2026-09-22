package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 원본 통화 1단위를 대상 통화로 바꾸는 양수 배율과 그 기준일입니다.
 * 소수 자릿수나 통화 단위 보정은 포함하지 않으며 표시 가격의 반올림은 변환 서비스가 담당합니다.
 */
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
