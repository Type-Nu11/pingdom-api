package com.typenull.pingdom.place.application.service.registration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Naver Geocoding 호출에만 적용할 base URL과 연결·응답 시간 제한 RestClient를 구성합니다. */
@Configuration
class NaverAddressSearchConfiguration {

    /**
     * 애플리케이션 공용 HTTP 설정과 분리해 주소 검색 외부 호출의 timeout 범위를 제한.
     * 인증값은 이 Bean에 보관하지 않고 요청을 보낼 때만 헤더로 전달합니다.
     */
    @Bean
    RestClient naverAddressSearchRestClient(NaverAddressSearchClient.Properties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
