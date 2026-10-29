package com.typenull.pingdom.place.infrastructure.localhot;

import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class NaverLocalRegionConfiguration {
    @Bean
    RestClient naverLocalRegionRestClient(NaverLocalRegionProperties properties, OiletProxyProperties oiletProxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        oiletProxy.applyTo(factory);
        return RestClient.builder().baseUrl(oiletProxy.targetUrl(properties.baseUrl())).requestFactory(factory).build();
    }
}
