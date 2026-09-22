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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
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

    /**
     * 생성된 OpenAPI에서 상담 도입 API의 첫 태그가 Consulting인지 확인해 문서 분류를 고정.
     */
    @Test
    void documentsConsultationIntroTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro'].post.tags[0]").value("Consulting"));
    }

    /**
     * 인증 없는 상담 도입 요청이 200과 Gemini 문구·source=gemini를 반환하는지 검증.
     */
    @Test
    void allowsAnonymousGeminiIntro() throws Exception {
        given(geminiIntroClient.generateIntro("카페를 열고 싶어요"))
                .willReturn(Optional.of("카페 창업을 고민하고 계시는군요. 카테고리를 선택해 주세요."));

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"카페를 열고 싶어요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("카페 창업을 고민하고 계시는군요. 카테고리를 선택해 주세요."))
                .andExpect(jsonPath("$.source").value("gemini"));
    }

    /**
     * 공백 메시지와 301자 메시지가 각각 400과 message 필드 검증 오류로 거절되는지 확인.
     */
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

    /**
     * Gemini 결과가 비어 있어도 상담 도입 API는 200과 fallback 출처를 반환하는지 검증.
     */
    @Test
    void returnsFallbackWithoutGeminiText() throws Exception {
        given(geminiIntroClient.generateIntro("빈 응답"))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/consultations/intro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"빈 응답\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("fallback"));
    }

    /**
     * 요청 제한 저장소가 거절하면 상담 도입 API가 429 RATE_LIMIT_EXCEEDED를 반환하는지 검증.
     */
    @Test
    void mapsIntroRateLimitFailure() throws Exception {
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
