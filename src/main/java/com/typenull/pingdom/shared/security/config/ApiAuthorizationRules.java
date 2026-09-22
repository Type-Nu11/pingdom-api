package com.typenull.pingdom.shared.security.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** API endpoint authorization policy kept separate from filter-chain wiring. */
@Component
public class ApiAuthorizationRules {
    private static final String[] OPENAPI_PATHS = {
            "/swagger-ui", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    private final boolean openApiPublicAccessEnabled;

    public ApiAuthorizationRules(
            @Value("${pingdom.openapi.public-access.enabled:false}") boolean openApiPublicAccessEnabled
    ) {
        this.openApiPublicAccessEnabled = openApiPublicAccessEnabled;
    }

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
