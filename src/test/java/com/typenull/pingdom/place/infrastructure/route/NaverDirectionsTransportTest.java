package com.typenull.pingdom.place.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private NaverDirectionsClient.Properties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.start();
        // 운영 Properties의 HTTPS 제약은 유지하고 로컬 소켓 테스트에서만 HTTP를 사용합니다.
        properties = mock(NaverDirectionsClient.Properties.class);
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
        try (var peer = new TricklingHttpPeer(2)) {
            when(properties.baseUrl()).thenReturn(peer.baseUrl());
            long start = System.nanoTime();
            assertThatThrownBy(() -> transport.get(ORIGIN, DESTINATION)).isInstanceOf(SocketTimeoutException.class)
                    .hasMessageContaining("전체 응답");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            assertThat(peer.chunks.get()).isGreaterThan(2);
            assertThat(peer.disconnected.await(3, TimeUnit.SECONDS)).as("deadline cancels socket I/O").isTrue();
            var response = transport.get(ORIGIN, DESTINATION);
            assertThat(response.status()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("ok");
            peer.serving.get(2, TimeUnit.SECONDS);
            assertThat(peer.calls.get()).isEqualTo(2);
        }
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
        try (var peer = new TricklingHttpPeer(1)) {
            when(properties.baseUrl()).thenReturn(peer.baseUrl());
            // interrupt 검증 중 전체 deadline이 먼저 도달해 결과를 혼동하지 않도록 분리한다.
            when(properties.requestTimeout()).thenReturn(Duration.ofSeconds(10));
            var interrupted = new AtomicBoolean();
            var caller = new Thread(() -> {
                try {
                    transport.get(ORIGIN, DESTINATION);
                } catch (IOException exception) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            caller.start();
            try {
                assertThat(peer.started.await(2, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                caller.join(2000);
                assertThat(caller.isAlive()).isFalse();
                assertThat(interrupted).isTrue();
                assertThat(peer.disconnected.await(3, TimeUnit.SECONDS)).as("interrupt cancels socket I/O").isTrue();
                peer.serving.get(2, TimeUnit.SECONDS);
            } finally {
                caller.interrupt();
                caller.join(2000);
            }
        }
    }

    /**
     * 취소 후 작은 응답 쓰기가 언제 실패하는지 대신, 연결 읽기의 EOF/reset을 직접 관측한다.
     * chunked 응답은 계속 진행하므로 전체 timeout이나 interrupt가 소켓을 닫아야 검증이 끝난다.
     */
    private final class TricklingHttpPeer implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket();
        private final ConcurrentLinkedQueue<Socket> sockets = new ConcurrentLinkedQueue<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger chunks = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
        private final Future<?> serving;

        private TricklingHttpPeer(int expectedRequests) throws IOException {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            serving = handlers.submit(() -> {
                for (int i = 0; i < expectedRequests; i++) {
                    try (Socket socket = listener.accept()) {
                        sockets.add(socket);
                        if (listener.isClosed()) return null;
                        socket.setSoTimeout(5_000);
                        var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        String line;
                        while ((line = input.readLine()) != null && !line.isEmpty()) { }
                        if (line == null) throw new IOException("HTTP 요청 헤더가 완료되지 않았습니다.");
                        var output = socket.getOutputStream();
                        if (calls.incrementAndGet() > 1) {
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                                    .getBytes(StandardCharsets.US_ASCII));
                            output.flush();
                            continue;
                        }
                        output.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                        output.flush();
                        var writer = handlers.submit(() -> {
                            try {
                                while (!socket.isClosed()) {
                                    output.write("1\r\nx\r\n".getBytes(StandardCharsets.US_ASCII));
                                    output.flush();
                                    chunks.incrementAndGet();
                                    started.countDown();
                                    pause(50);
                                }
                            } catch (IOException ignored) {
                                // 쓰기 실패는 종료 판단에 사용하지 않는다. 읽기에서 실제 EOF/reset을 확인한다.
                            }
                        });
                        try {
                            if (input.read() == -1) disconnected.countDown();
                        } catch (SocketException reset) {
                            disconnected.countDown();
                        } finally {
                            writer.cancel(true);
                        }
                    }
                }
                return null;
            });
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + listener.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            listener.close();
            for (Socket socket : sockets) socket.close();
            serving.cancel(true);
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
