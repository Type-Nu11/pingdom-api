package com.typenull.pingdom.place.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 실제 소켓으로 전송 정책을 검증합니다. 외부 공급자 호출이나 Docker가 필요하지 않습니다. */
class NaverDirectionsTransportTest {
    private static final RouteCoordinate ORIGIN = new RouteCoordinate(37.5665, 126.9780);
    private static final RouteCoordinate DESTINATION = new RouteCoordinate(37.4979, 127.0276);
    private HttpServer server;
    private ExecutorService handlers;
    private CloseableHttpAsyncClient client;
    private NaverDirectionsTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.start();
        // 운영 Properties의 HTTPS 제약은 유지하고 로컬 소켓 테스트에서만 HTTP를 사용합니다.
        var properties = mock(NaverDirectionsClient.Properties.class);
        when(properties.baseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(properties.clientId()).thenReturn("test-id");
        when(properties.clientSecret()).thenReturn("test-secret");
        when(properties.connectTimeout()).thenReturn(Duration.ofMillis(300));
        when(properties.readTimeout()).thenReturn(Duration.ofMillis(300));
        when(properties.requestTimeout()).thenReturn(Duration.ofMillis(800));
        client = new NaverDirectionsConfiguration().naverDirectionsHttpClient(properties);
        client.start();
        transport = new NaverDirectionsTransport(client, properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.stop(0);
        handlers.shutdownNow();
    }

    @Test
    void sendsContractAndWaitsForCompleteBody() throws Exception {
        var request = new AtomicReference<String>();
        var credentials = new AtomicReference<String>();
        server.createContext("/map-direction/v1/driving", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            credentials.set(exchange.getRequestHeaders().getFirst("x-ncp-apigw-api-key-id") + ":"
                    + exchange.getRequestHeaders().getFirst("x-ncp-apigw-api-key"));
            byte[] body = "complete response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body, 0, 4);
                output.flush();
                pause(50);
                output.write(body, 4, body.length - 4);
            }
        });
        var response = transport.get(ORIGIN, DESTINATION);
        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("complete response");
        assertThat(request.get()).isEqualTo("GET /map-direction/v1/driving?start=126.978,37.5665&goal=127.0276,37.4979&option=traoptimal");
        assertThat(credentials.get()).isEqualTo("test-id:test-secret");
    }

    @Test
    void cancelsTricklingBodyAtOverallDeadlineAndAllowsNextRequest() throws Exception {
        var calls = new AtomicInteger();
        var chunks = new AtomicInteger();
        var disconnected = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            if (calls.incrementAndGet() > 1) {
                exchange.sendResponseHeaders(200, 2);
                try (var output = exchange.getResponseBody()) { output.write("ok".getBytes(StandardCharsets.UTF_8)); }
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    output.write('x');
                    output.flush();
                    chunks.incrementAndGet();
                    pause(50);
                }
            } catch (IOException exception) {
                disconnected.countDown();
            }
        });
        long start = System.nanoTime();
        assertThatThrownBy(() -> transport.get(ORIGIN, DESTINATION)).isInstanceOf(SocketTimeoutException.class)
                .hasMessageContaining("전체 응답");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(chunks.get()).isGreaterThan(2);
        assertThat(disconnected.await(3, TimeUnit.SECONDS)).as("deadline cancels socket I/O").isTrue();
        assertThat(transport.get(ORIGIN, DESTINATION).status()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryWhenPeerClosesBeforeHeaders() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.close();
        });
        assertThatThrownBy(() -> transport.get(ORIGIN, DESTINATION)).isInstanceOf(IOException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void doesNotRetryStatusEvenWithRetryAfter(int status) throws Exception {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        assertThat(transport.get(ORIGIN, DESTINATION).status()).isEqualTo(status);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotFollowRedirectOrForwardCredentials() throws Exception {
        var redirected = new AtomicInteger();
        server.createContext("/map-direction", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        assertThat(transport.get(ORIGIN, DESTINATION).status()).isEqualTo(302);
        assertThat(redirected.get()).isZero();
    }

    @Test
    void interruptionCancelsExchangeAndPreservesInterruptFlag() throws Exception {
        var started = new CountDownLatch(1);
        var disconnected = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    output.write('x');
                    output.flush();
                    started.countDown();
                    pause(50);
                }
            } catch (IOException exception) {
                disconnected.countDown();
            }
        });
        var caller = new Thread(() -> {
            try {
                transport.get(ORIGIN, DESTINATION);
            } catch (IOException exception) {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(interrupted).isTrue();
            assertThat(disconnected.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            caller.interrupt();
            caller.join(2000);
        }
    }

    private static void pause(long milliseconds) throws IOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }
}
