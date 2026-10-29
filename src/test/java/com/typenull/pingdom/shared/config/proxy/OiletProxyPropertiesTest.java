package com.typenull.pingdom.shared.config.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Oilet는 CONNECT를 받지 않으므로 평문 절대 URI 요청이 프록시에 도달하는지 실제 소켓으로 검증합니다. */
class OiletProxyPropertiesTest {

    @Test
    void rewritesHttpsTargetOnlyWhenEnabled() {
        var enabled = new OiletProxyProperties(true, "127.0.0.1", 10010);
        var disabled = new OiletProxyProperties(false, "127.0.0.1", 10010);

        assertThat(enabled.targetUrl("https://api.frankfurter.dev/v2")).isEqualTo("http://api.frankfurter.dev:443/v2");
        assertThat(enabled.targetUrl("https://dapi.kakao.com")).isEqualTo("http://dapi.kakao.com:443");
        assertThat(enabled.targetUrl("https://example.com:8443/a")).isEqualTo("http://example.com:8443/a");
        assertThat(enabled.targetUrl("http://localhost:11434")).isEqualTo("http://localhost:11434");
        assertThat(disabled.targetUrl("https://dapi.kakao.com")).isEqualTo("https://dapi.kakao.com");
    }

    @Test
    void restClientSendsAbsoluteUriToProxy() throws Exception {
        try (var proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var oilet = new OiletProxyProperties(true, "127.0.0.1", proxy.getLocalPort());
            var requestLines = respondOnce(proxy);
            var factory = new SimpleClientHttpRequestFactory();
            oilet.applyTo(factory);

            RestClient.builder().baseUrl(oilet.targetUrl("https://dapi.kakao.com")).requestFactory(factory).build()
                    .get().uri("/v2/local").retrieve().toBodilessEntity();

            assertThat(requestLines.get(5, TimeUnit.SECONDS))
                    .contains("GET http://dapi.kakao.com:443/v2/local HTTP/1.1")
                    .anySatisfy(line -> assertThat(line).isEqualToIgnoringCase("Host: dapi.kakao.com:443"));
        }
    }

    @Test
    void asyncClientSendsAbsoluteUriToProxy() throws Exception {
        try (var proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var oilet = new OiletProxyProperties(true, "127.0.0.1", proxy.getLocalPort());
            var requestLines = respondOnce(proxy);
            try (var client = oilet.applyTo(HttpAsyncClients.custom()).build()) {
                client.start();
                var request = SimpleRequestBuilder.get(oilet.targetUrl("https://apis.openapi.sk.com") + "/transit/routes")
                        .build();
                client.execute(request, null).get(5, TimeUnit.SECONDS);
            }

            assertThat(requestLines.get(5, TimeUnit.SECONDS))
                    .contains("GET http://apis.openapi.sk.com:443/transit/routes HTTP/1.1")
                    .anySatisfy(line -> assertThat(line).isEqualToIgnoringCase("Host: apis.openapi.sk.com:443"))
                    .noneMatch(line -> line.startsWith("CONNECT"));
        }
    }

    private CompletableFuture<List<String>> respondOnce(ServerSocket proxy) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = proxy.accept()) {
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                var lines = new ArrayList<String>();
                for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                    lines.add(line);
                }
                OutputStream out = socket.getOutputStream();
                out.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return lines;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
