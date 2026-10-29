package com.typenull.pingdom.place.infrastructure.route;

import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
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
class TmapTransitConfiguration {
    @Bean(initMethod = "start", destroyMethod = "close")
    CloseableHttpAsyncClient tmapTransitHttpClient(TmapTransitClient.Properties properties, OiletProxyProperties oiletProxy) {
        var connections = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeout().toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis())).build())
                .setMaxConnTotal(20).setMaxConnPerRoute(20).build();
        return oiletProxy.applyTo(HttpAsyncClients.custom()).setConnectionManager(connections)
                .setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(2).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.requestTimeout().toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis()))
                        .setAuthenticationEnabled(false).build())
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    }
}
