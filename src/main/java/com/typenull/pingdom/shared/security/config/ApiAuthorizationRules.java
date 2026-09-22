package com.typenull.pingdom.shared.security.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** 공개·관리자·일반 인증 경로를 순서대로 선언. 세부 역할 검증은 각 메서드 보안과 함께 적용됨. */
@Component
public class ApiAuthorizationRules {
    private static final String[] OPENAPI_PATHS = {
            "/swagger-ui", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    private final boolean openApiPublicAccessEnabled;

    /** Swagger 공개 접근 설정을 주입하며 미설정 시 인증을 요구. */
    public ApiAuthorizationRules(
            @Value("${pingdom.openapi.public-access.enabled:false}") boolean openApiPublicAccessEnabled
    ) {
        this.openApiPublicAccessEnabled = openApiPublicAccessEnabled;
    }

    /** 공개 경로를 먼저 허용하고 Swagger 공개 설정·관리자 경로를 적용한 뒤 나머지 요청에 인증을 요구. */
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/auth/**", "/error", "/actuator/health", "/actuator/health/**").permitAll();
        if (openApiPublicAccessEnabled) {
            auth.requestMatchers(OPENAPI_PATHS).permitAll();
        }
        auth
                .requestMatchers("/consultations/intro").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated();
    }
}
