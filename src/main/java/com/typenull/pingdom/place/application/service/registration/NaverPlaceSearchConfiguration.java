package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** API HUB 지역 검색의 endpoint와 timeout을 다른 Naver 연동과 분리합니다. */
@Configuration
class NaverPlaceSearchConfiguration {

    @Bean
    RestClient naverPlaceSearchRestClient(NaverPlaceSearchClient.Properties properties, OiletProxyProperties oiletProxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        oiletProxy.applyTo(factory);
        return RestClient.builder().baseUrl(oiletProxy.targetUrl(properties.baseUrl())).requestFactory(factory).build();
    }
}
