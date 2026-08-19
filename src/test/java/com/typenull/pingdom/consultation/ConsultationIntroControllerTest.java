package com.typenull.pingdom.consultation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.consultation.application.GeminiIntroClient;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "gemini.enabled=true",
        "gemini.api-key=test-api-key"
})
@AutoConfigureMockMvc
class ConsultationIntroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeminiIntroClient geminiIntroClient;

    @MockBean
    private RateLimitStore rateLimitStore;

    @Test
    void documentsConsultationIntroInWebTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro'].post.tags[0]").value("Web"));
    }

    @Test
    void allowsAnonymousRequestAndReturnsGeminiContract() throws Exception {
        given(geminiIntroClient.generateIntro("카페를 열고 싶어요"))
                .willReturn(Optional.of("카페 창업을 고민하고 계시는군요. 카테고리를 선택해 주세요."));

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"카페를 열고 싶어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("카페 창업을 고민하고 계시는군요. 카테고리를 선택해 주세요."))
                .andExpect(jsonPath("$.source").value("gemini"));
    }

    @Test
    void rejectsBlankOrOversizedMessage() throws Exception {
        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"%s\"}".formatted("a".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    void returnsFallbackWhenGeminiReturnsNoText() throws Exception {
        given(geminiIntroClient.generateIntro("빈 응답"))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"빈 응답\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"));
    }

    @Test
    void returnsTooManyRequestsWhenRateLimitRejectsIp() throws Exception {
        org.mockito.BDDMockito.willThrow(new RateLimitException("요청 횟수가 너무 많습니다."))
                .given(rateLimitStore)
                .acquire(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"카페를 열고 싶어요\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
