package com.typenull.pingdom.consultation.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiGenerateContentClientTest {

    private MockRestServiceServer server;
    private GeminiGenerateContentClient client;

    /**
     * 가짜 Gemini HTTP 서버와 모델·키·시간 제한 설정을 연결해 실제 공급자 호출 없이 요청 계약을 검증.
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://gemini.test/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiGenerateContentClient(
                builder.build(),
                new GeminiProperties(true, "test-api-key", "gemini-test", Duration.ofSeconds(2), Duration.ofSeconds(5))
        );
    }

    /**
     * API 키·JSON 헤더·사용자/시스템 문구와 출력 80토큰·후보 1개 제한을 전송하고 여러 응답 part를 한 문장으로 합치는지 검증.
     */
    @Test
    void requestsBoundedIntroCandidate() {
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andExpect(header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("카페를 열고 싶어요"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").exists())
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(80))
                .andExpect(jsonPath("$.generationConfig.candidateCount").value(1))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"카페 창업을 고민하고 계시는군요."},{"text":"카테고리를 선택해 주세요."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generateIntro("카페를 열고 싶어요"))
                .contains("카페 창업을 고민하고 계시는군요. 카테고리를 선택해 주세요.");
        server.verify();
    }

    /**
     * Gemini 응답 후보가 비어 있으면 빈 Optional을 반환해 상위 서비스의 대체 안내 경로를 지원하는지 검증.
     */
    @Test
    void returnsEmptyWithoutTextCandidate() {
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.generateIntro("카페를 열고 싶어요")).isEmpty();
        server.verify();
    }
}
