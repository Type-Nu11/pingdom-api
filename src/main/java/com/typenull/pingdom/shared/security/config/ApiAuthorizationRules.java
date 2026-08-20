package com.typenull.pingdom.shared.security.config;

import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** API endpoint authorization policy kept separate from filter-chain wiring. */
@Component
public class ApiAuthorizationRules {
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/auth/**", "/error", "/actuator/health", "/actuator/health/**",
                        "/swagger-ui", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/consultations/intro", "/analysis/reports/location").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated();
    }
}
