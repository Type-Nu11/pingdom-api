package com.typenull.pingdom.menu.infrastructure.currency;

import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class FrankfurterCurrencyExchangeConfiguration {

    @Bean
    RestClient menuCurrencyExchangeRestClient(FrankfurterCurrencyExchangeProperties properties, OiletProxyProperties oiletProxy) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        oiletProxy.applyTo(requestFactory);
        return RestClient.builder()
                .baseUrl(oiletProxy.targetUrl(properties.baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }
}
