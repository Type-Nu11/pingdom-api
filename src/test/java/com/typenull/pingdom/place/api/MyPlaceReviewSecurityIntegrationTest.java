package com.typenull.pingdom.place.api;

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
class MyPlaceReviewSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** 인증 없이 내 리뷰를 요청하면 보안 체인이 401과 INVALID_TOKEN을 반환하는지 확인. */
    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/users/me/reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
