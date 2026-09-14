package com.typenull.pingdom.menu.infrastructure.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.menu.application.currency.CurrencyExchangeRate;
import com.typenull.pingdom.menu.application.currency.CurrencyExchangeRateClient;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class FrankfurterCurrencyExchangeRateClient implements CurrencyExchangeRateClient {

    private final RestClient restClient;
    private final FrankfurterCurrencyExchangeProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<CurrencyPair, CachedRate> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CurrencyPair, CompletableFuture<Optional<CurrencyExchangeRate>>> inFlightRequests
            = new ConcurrentHashMap<>();

    public FrankfurterCurrencyExchangeRateClient(
            @Qualifier("menuCurrencyExchangeRestClient") RestClient menuCurrencyExchangeRestClient,
            FrankfurterCurrencyExchangeProperties properties,
            Clock clock
    ) {
        this.restClient = menuCurrencyExchangeRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<CurrencyExchangeRate> findRate(MenuCurrency sourceCurrency, MenuCurrency targetCurrency) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        CurrencyPair pair = new CurrencyPair(sourceCurrency, targetCurrency);
        CachedRate cached = cache.get(pair);
        Instant now = Instant.now(clock);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(cached.exchangeRate());
        }

        CompletableFuture<Optional<CurrencyExchangeRate>> newRequest = new CompletableFuture<>();
        CompletableFuture<Optional<CurrencyExchangeRate>> inFlight = inFlightRequests.putIfAbsent(pair, newRequest);
        if (inFlight != null) {
            return inFlight.join();
        }

        try {
            Optional<CurrencyExchangeRate> fetched = requestRate(sourceCurrency, targetCurrency);
            fetched.ifPresent(rate -> cache.put(pair, new CachedRate(rate, now.plus(properties.cacheTtl()))));
            newRequest.complete(fetched);
            return fetched;
        } catch (RuntimeException exception) {
            Optional<CurrencyExchangeRate> unavailable = Optional.empty();
            newRequest.complete(unavailable);
            return unavailable;
        } finally {
            inFlightRequests.remove(pair, newRequest);
        }
    }

    private Optional<CurrencyExchangeRate> requestRate(MenuCurrency sourceCurrency, MenuCurrency targetCurrency) {
        try {
            JsonNode response = restClient.get()
                    .uri("/rate/{source}/{target}", sourceCurrency.name(), targetCurrency.name())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("rate").isNumber() || !response.path("date").isTextual()) {
                return Optional.empty();
            }
            BigDecimal rate = response.path("rate").decimalValue();
            LocalDate rateDate = LocalDate.parse(response.path("date").asText());
            return Optional.of(new CurrencyExchangeRate(sourceCurrency, targetCurrency, rate, rateDate));
        } catch (RestClientException | DateTimeException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private record CurrencyPair(MenuCurrency sourceCurrency, MenuCurrency targetCurrency) {
    }

    private record CachedRate(CurrencyExchangeRate exchangeRate, Instant expiresAt) {
    }
}
