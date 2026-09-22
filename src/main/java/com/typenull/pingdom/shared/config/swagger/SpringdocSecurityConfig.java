package com.typenull.pingdom.shared.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서에서 참조할 bearerAuth JWT 인증 방식을 선언한다. 개별 요청의 인증 요구 여부는 별도 구성이다. */
@Configuration
public class SpringdocSecurityConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }
}
