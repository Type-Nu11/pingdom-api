package com.typenull.pingdom.menu.application.currency;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.util.Optional;

public interface CurrencyExchangeRateClient {
    Optional<CurrencyExchangeRate> findRate(MenuCurrency sourceCurrency, MenuCurrency targetCurrency);
}
