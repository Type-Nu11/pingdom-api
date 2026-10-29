package com.typenull.pingdom.shared.config.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 외부 API 호출을 Oilet egress 프록시로 보내기 위한 설정을 바인딩.
 * Oilet는 CONNECT를 받지 않는 TLS 종료 게이트웨이이므로, 켜져 있을 때만 https 대상 주소를
 * http://host:443 으로 바꿔 평문 절대 URI 요청으로 보내고 바깥 TLS는 Oilet가 맺습니다.
 * 설정의 base URL은 항상 https로 두어, 프록시 없이 평문으로 인증 헤더가 나가는 경우를 막습니다.
 * S3·FCM·Postmark처럼 SDK가 CONNECT로 프록시를 타는 클라이언트에는 적용하지 않습니다.
 */
@ConfigurationProperties(prefix = "oilet.proxy")
public record OiletProxyProperties(
        boolean enabled,
        String host,
        int port
) {

    private static final int HTTPS_PORT = 443;

    public void applyTo(SimpleClientHttpRequestFactory requestFactory) {
        if (enabled) {
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }
    }

    public HttpAsyncClientBuilder applyTo(HttpAsyncClientBuilder builder) {
        return enabled ? builder.setProxy(new HttpHost(host, port)) : builder;
    }

    /** 프록시가 켜져 있으면 https 대상 주소를 Oilet가 받는 http://host:443 형태로 바꿉니다. */
    public String targetUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!enabled || !"https".equalsIgnoreCase(uri.getScheme())) {
            return baseUrl;
        }
        int targetPort = uri.getPort() == -1 ? HTTPS_PORT : uri.getPort();
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        String rawQuery = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return "http://" + uri.getHost() + ":" + targetPort + rawPath + rawQuery;
    }
}
