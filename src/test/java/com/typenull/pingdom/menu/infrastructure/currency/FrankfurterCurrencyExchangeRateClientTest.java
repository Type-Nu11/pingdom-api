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
import java.time.ZoneId;
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

    /**
     * 실제 네트워크 대신 MockRestServiceServer를 연결하고 고정 Clock으로 환율 캐시 기준 시각을 맞춘다.
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://frankfurter.test/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        client = newClient(builder, Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * 환율 JSON의 값·기준일을 매핑하고 같은 통화 쌍의 재조회가 추가 HTTP 요청 없이 같은 결과를 반환하는지 검증한다.
     */
    @Test
    void mapsAndCachesCurrencyPair() {
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

    /**
     * 환율 공급자 응답이 500이면 예외 대신 빈 결과를 반환하는지 검증한다.
     */
    @Test
    void returnsEmptyWhenProviderFails() {
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withServerError());

        assertThat(client.findRate(MenuCurrency.KRW, MenuCurrency.USD)).isEmpty();
        server.verify();
    }

    /**
     * 첫 500 응답 이후 다시 조회하면 성공 환율을 받아오는지 확인해 실패 결과가 캐시되는 회귀를 방지한다.
     */
    @Test
    void retriesAfterProviderFailure() {
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withServerError());
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withSuccess("""
                        {"date":"2026-09-10","base":"KRW","quote":"USD","rate":0.000715}
                        """, MediaType.APPLICATION_JSON));

        Optional<CurrencyExchangeRate> failed = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);
        Optional<CurrencyExchangeRate> recovered = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);

        assertThat(failed).isEmpty();
        assertThat(recovered).hasValueSatisfying(rate -> assertThat(rate.rate()).isEqualByComparingTo("0.000715"));
        server.verify();
    }

    /**
     * Clock을 캐시 TTL인 10분만큼 전진하면 기존 0.000714 대신 새 환율 0.000715를 조회하는지 검증한다.
     */
    @Test
    void refreshesExpiredRate() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://frankfurter.test/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
        client = newClient(builder, clock);
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withSuccess("""
                        {"date":"2026-09-09","base":"KRW","quote":"USD","rate":0.000714}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://frankfurter.test/v2/rate/KRW/USD"))
                .andRespond(withSuccess("""
                        {"date":"2026-09-10","base":"KRW","quote":"USD","rate":0.000715}
                        """, MediaType.APPLICATION_JSON));

        Optional<CurrencyExchangeRate> cached = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);
        clock.advance(Duration.ofMinutes(10));
        Optional<CurrencyExchangeRate> refreshed = client.findRate(MenuCurrency.KRW, MenuCurrency.USD);

        assertThat(cached).hasValueSatisfying(rate -> assertThat(rate.rate()).isEqualByComparingTo("0.000714"));
        assertThat(refreshed).hasValueSatisfying(rate -> assertThat(rate.rate()).isEqualByComparingTo("0.000715"));
        server.verify();
    }

    /**
     * 첫 환율 요청의 응답을 latch로 보류한 상태에서 두 번째 조회를 제출해 두 결과가 같고 HTTP 요청은 한 번인지 검증한다.
     * Future와 latch의 대기 상한을 두고 executor를 종료해 실패 시에도 테스트 자원을 회수한다.
     */
    @Test
    void coalescesConcurrentCurrencyMisses() throws Exception {
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

    /**
     * 2초 연결·3초 읽기 제한과 10분 캐시 TTL을 가진 클라이언트를 주어진 RestClient·Clock으로 생성한다.
     */
    private FrankfurterCurrencyExchangeRateClient newClient(RestClient.Builder builder, Clock clock) {
        return new FrankfurterCurrencyExchangeRateClient(builder.build(),
                new FrankfurterCurrencyExchangeProperties(true, "https://frankfurter.test/v2", Duration.ofSeconds(2),
                        Duration.ofSeconds(3), Duration.ofMinutes(10)), clock);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        /**
         * 캐시 만료 경계에서 사용할 변경 가능한 현재 시각을 초기화한다.
         */
        private MutableClock(Instant current) {
            this.current = current;
        }

        /**
         * 테스트 시각 계산의 시간대를 UTC로 고정한다.
         */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * 테스트용 Clock은 UTC만 사용하므로 요청 시간대와 무관하게 같은 인스턴스를 반환한다.
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /**
         * 테스트에서 직접 전진시킨 현재 Instant를 캐시 시간 판정에 제공한다.
         */
        @Override
        public Instant instant() {
            return current;
        }

        /**
         * 실제 대기 없이 현재 시각에 지정한 기간을 더해 캐시 TTL 경계를 재현한다.
         */
        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
