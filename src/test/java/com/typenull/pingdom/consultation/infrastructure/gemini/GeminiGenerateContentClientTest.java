package com.typenull.pingdom.consultation.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.application.ProviderEnvelopeValidator;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void requestsTypedEnvelopeWithIndependentRequestIds() throws Exception {
        var mapper = new ObjectMapper();
        for (String requestId : java.util.List.of("request-1", "request-2")) {
            String envelope = mapper.writeValueAsString(java.util.Map.of(
                    "schemaVersion", 1, "id", requestId, "kind", "assistant_message", "text", "안녕하세요"));
            String response = mapper.writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
                    java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", envelope)))))));
            server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                    .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                    .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(512))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf.length()").value(8))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[6].properties.schemaVersion.type").value("integer"))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[6].properties.schemaVersion.enum[0]").value(1))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[6].properties.kind.enum[0]").value("assistant_message"))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[*].properties.id.enum[0]")
                            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(requestId))))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[6].required")
                            .value(org.hamcrest.Matchers.containsInAnyOrder("schemaVersion", "id", "kind", "text")))
                    .andExpect(jsonPath("$.generationConfig.responseJsonSchema.anyOf[6].additionalProperties").value(false))
                    .andExpect(jsonPath("$.systemInstruction.parts[0].text")
                            .value(org.hamcrest.Matchers.containsString("\nrequest id: " + requestId)))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
        for (String requestId : java.util.List.of("request-1", "request-2")) {
            var actual = client.generateEnvelope("안녕하세요", requestId);
            new ProviderEnvelopeValidator().validate(actual, requestId);
        }
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 503, 504})
    void logsProviderHttpStatusWithoutResponseBody(int status, CapturedOutput output) {
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("private-provider-body test-api-key"));
        assertThatThrownBy(() -> client.generateEnvelope("private-user-message", "request-1"))
                .isInstanceOf(RestClientResponseException.class);
        String outcome = status == 401 || status == 403 ? "authentication"
                : status == 429 ? "quota" : status == 504 ? "timeout" : "http_error";
        assertThat(output).contains("outcome=" + outcome, "httpStatus=" + status)
                .contains("model=gemini-test elapsedMs=")
                .doesNotContain("private-provider-body", "test-api-key", "private-user-message");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "private-invalid-json"})
    void logsMissingOrUnparseableTextWithoutContent(String text, CapturedOutput output) throws Exception {
        String response = new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
                java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", text)))))));
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThat(client.generateEnvelope("private-user-message", "request-1")).isNull();
        assertThat(output).contains("reason=" + (text.isEmpty() ? "missing_text" : "invalid_json"))
                .contains("outcome=http_success httpStatus=200 model=gemini-test elapsedMs=")
                .doesNotContain("private-invalid-json", "private-user-message", "test-api-key");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void logsTransportClassificationWithoutExceptionMessage(boolean timeout, CapturedOutput output) {
        java.io.IOException failure = timeout ? new java.net.SocketTimeoutException("private-provider-url test-api-key")
                : new java.io.IOException("private-provider-url test-api-key");
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(failure));
        assertThatThrownBy(() -> client.generateEnvelope("private-user-message", "request-1"))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        assertThat(output).contains("outcome=" + (timeout ? "timeout" : "transport_error"))
                .contains("model=gemini-test elapsedMs=")
                .doesNotContain("private-provider-url", "test-api-key", "private-user-message");
        server.verify();
    }

    @Test
    void correlatesSuccessfulHttpCallWithRequestHeaderId() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GeminiGenerateContentClient.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        org.slf4j.MDC.put("requestId", "http-trace-1759");
        try {
            String envelope = "{\"schemaVersion\":1,\"id\":\"private-replay-id\",\"kind\":\"assistant_message\",\"text\":\"private-reply\"}";
            expectEnvelopeResponse(envelope);
            var actual = client.generateEnvelope("private-user-message", "private-replay-id");
            new ProviderEnvelopeValidator().validate(actual, "private-replay-id");

            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "http-trace-1759");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain(
                        "private-user-message", "private-reply", "private-replay-id", "test-api-key");
            });
            assertThat(appender.list.get(0).getFormattedMessage()).contains("outcome=started model=gemini-test");
            assertThat(appender.list.get(1).getFormattedMessage())
                    .contains("outcome=http_success httpStatus=200 model=gemini-test")
                    .matches(".*elapsedMs=[0-9]+$");
            server.verify();
        } finally {
            org.slf4j.MDC.remove("requestId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"command\":\"searchNearbyReservablePlaces\",\"args\":{\"date\":\"2026-10-03\",\"startTime\":\"14:00\",\"endTime\":\"17:00\",\"quantity\":2,\"useCurrentLocation\":true}}",
            "{\"command\":\"getPlaceDetails\",\"args\":{\"placeId\":1}}",
            "{\"command\":\"getAvailabilities\",\"args\":{\"placeId\":1,\"date\":\"2026-10-03\",\"quantity\":2}}",
            "{\"command\":\"prepareReservation\",\"args\":{\"placeId\":1,\"availabilityId\":2,\"quantity\":2}}",
            "{\"command\":\"cancelVoiceSession\",\"args\":{}}"
    })
    void preservesExistingCommandsThroughProviderResponseParsing(String fields) throws Exception {
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(fields);
        envelope.put("schemaVersion", 1).put("id", "request-1").put("kind", "command_request");
        expectEnvelopeResponse(envelope.toString());
        var actual = client.generateEnvelope("private-user-message", "request-1");
        new ProviderEnvelopeValidator().validate(actual, "request-1");
        assertThat(actual).isEqualTo(envelope);
        server.verify();
    }

    private void expectEnvelopeResponse(String envelope) throws Exception {
        String response = new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
                java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", envelope)))))));
        server.expect(requestTo("https://gemini.test/v1beta/models/gemini-test:generateContent"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
