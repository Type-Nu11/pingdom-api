package com.typenull.pingdom.menu.infrastructure.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.menu.application.currency.CurrencyExchangeRate;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FrankfurterCurrencyExchangeRateClientTest {

    private MockRestServiceServer server;
    private FrankfurterCurrencyExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://frankfurter.test/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FrankfurterCurrencyExchangeRateClient(builder.build(),
                new FrankfurterCurrencyExchangeProperties(true, "https://frankfurter.test/v2", Duration.ofSeconds(2),
                        Duration.ofSeconds(3), Duration.ofMinutes(10)),
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void mapsRateResponseAndCachesCurrencyPair() {
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withSuccess("""
                        {"date":"2026-09-09","base":"KRW","quote":"USD","rate":0.000714}
                        """, MediaType.APPLICATION_JSON));

        Optional<CurrencyExchangeRate> first = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);
        Optional<CurrencyExchangeRate> second = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);

        assertThat(first).hasValueSatisfying(rate -> {
            assertThat(rate.rate()).isEqualByComparingTo("0.000714");
            assertThat(rate.rateDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        });
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void returnsEmptyWhenProviderFails() {
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withServerError());

        assertThat(client.findRate(MenuCurrency.KRW, MenuCurrency.USD)).isEmpty();
        server.verify();
    }

    @Test
    void coalescesConcurrentCacheMissesForSameCurrencyPair() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.expect(once(), requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(request -> {
                    requestStarted.countDown();
                    try {
                        assertThat(releaseResponse.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("환율 응답 대기 중 인터럽트가 발생했습니다.", exception);
                    }
                    return withSuccess("""
                            {"date":"2026-09-09","base":"KRW","quote":"USD","rate":0.000714}
                            """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<CurrencyExchangeRate>> first = executor.submit(
                    () -> client.findRate(MenuCurrency.KRW, MenuCurrency.USD));
            assertThat(requestStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Optional<CurrencyExchangeRate>> second = executor.submit(
                    () -> client.findRate(MenuCurrency.KRW, MenuCurrency.USD));
            releaseResponse.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        server.verify();
    }
}
