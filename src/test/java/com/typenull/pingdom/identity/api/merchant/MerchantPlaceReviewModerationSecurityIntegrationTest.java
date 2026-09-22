package com.typenull.pingdom.identity.api.merchant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class MerchantPlaceReviewModerationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 인증 없이 점주 리뷰 목록에 접근하면 보안 필터가 401과 INVALID_TOKEN을 반환하는지 검증.
     */
    @Test
    void listRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/merchant-owner/places/10/reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
