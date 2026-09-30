package com.typenull.pingdom.place.infrastructure.route;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NaverDirectionsConfiguration {
    @Bean(initMethod = "start", destroyMethod = "close")
    CloseableHttpAsyncClient naverDirectionsHttpClient(NaverDirectionsClient.Properties properties) {
        var connections = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeout().toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis())).build())
                .setMaxConnTotal(20).setMaxConnPerRoute(20).build();
        return HttpAsyncClients.custom()
                .setConnectionManager(connections)
                .setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(2).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.requestTimeout().toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis()))
                        .setAuthenticationEnabled(false).build())
                // 과금 요청 중복 및 리다이렉트 대상에 대한 인증 헤더 유출을 방지합니다.
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    }
}
