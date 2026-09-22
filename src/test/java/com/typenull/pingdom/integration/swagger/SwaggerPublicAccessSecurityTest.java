package com.typenull.pingdom.integration.swagger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** dev 프로필 없이 문서만 공개해도 일반 API의 인증 경계가 유지되는지 검증. */
@Tag("integration")
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "pingdom.openapi.public-access.enabled=true"
})
@AutoConfigureMockMvc
@Import(SwaggerPublicAccessSecurityTest.ProtectedController.class)
class SwaggerPublicAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesDocsWithoutDevelopmentProfile() throws Exception {
        for (String path : new String[]{
                "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css",
                "/swagger-ui/swagger-ui-bundle.js", "/v3/api-docs",
                "/v3/api-docs/swagger-config", "/v3/api-docs/app",
                "/v3/api-docs/common", "/v3/api-docs/consulting",
                "/v3/api-docs/admin", "/v3/api-docs/merchant"
        }) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void keepsBusinessAndAdminEndpointsProtected() throws Exception {
        mockMvc.perform(get("/security-regression/private")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/security-regression")).andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProtectedController {
        @GetMapping({"/security-regression/private", "/admin/security-regression"})
        String protectedResource() {
            return "protected";
        }
    }
}
