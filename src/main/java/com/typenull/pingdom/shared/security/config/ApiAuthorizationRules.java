package com.typenull.pingdom.shared.security.config;

import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** 공개·관리자·일반 인증 경로를 순서대로 선언. 세부 역할 검증은 각 메서드 보안과 함께 적용됨. */
@Component
public class ApiAuthorizationRules {
    /** 오류 디스패치와 명시된 공개 경로를 먼저 허용하고, 관리자 경로를 제한한 뒤 나머지 요청에 인증을 요구. */
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/auth/**", "/error", "/actuator/health", "/actuator/health/**",
                        "/swagger-ui", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/consultations/intro").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated();
    }
}
