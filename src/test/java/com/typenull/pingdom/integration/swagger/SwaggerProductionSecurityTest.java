package com.typenull.pingdom.integration.swagger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "pingdom.openapi.public-access.enabled=false"
})
@AutoConfigureMockMvc
class SwaggerProductionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthenticationForSwaggerUiAndApiDocs() throws Exception {
        for (String path : new String[]{
                "/swagger-ui",
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/v3/api-docs/app",
                "/v3/api-docs/common",
                "/v3/api-docs/consulting",
                "/v3/api-docs/admin",
                "/v3/api-docs/merchant"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
        }
    }
}
