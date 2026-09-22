package com.typenull.pingdom.analysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.analysis.infrastructure.mcp.McpAnalysisProperties;
import java.time.LocalDate;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAiAnalysisClientTest {

    /**
     * Gemini interactions 요청이 모델·프롬프트·generation_config의 tool_choice와 MCP URL·허용 도구·인증 헤더를 올바르게 담는지 검증한다.
     * 금지된 요청 필드가 없고 완료 응답에서 보고서명과 기준일을 복원하는지 확인한다.
     */
    @Test
    void registersMcpAndParsesOutput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gemini-3.1-flash-lite"))
                .andExpect(jsonPath("$.input").value("prompt"))
                .andExpect(jsonPath("$.generation_config.tool_choice").value("auto"))
                .andExpect(jsonPath("$.generation_config.temperature").doesNotExist())
                .andExpect(jsonPath("$.generation_config.max_output_tokens").doesNotExist())
                .andExpect(jsonPath("$.tool_choice").doesNotExist())
                .andExpect(jsonPath("$.tools[0].type").value("mcp_server"))
                .andExpect(jsonPath("$.tools[0].name").value("pingdom_mcp"))
                .andExpect(jsonPath("$.tools[0].url").value("https://mcp.test/mcp"))
                .andExpect(jsonPath("$.tools[0].allowed_tools[0].mode").value("any"))
                .andExpect(jsonPath("$.tools[0].allowed_tools[0].tools[0]").value("recommend_location"))
                .andExpect(jsonPath("$.tools[0].headers.Authorization")
                        .value("Bearer mcp-secret"))
                .andRespond(withSuccess(interactionResponse(), MediaType.APPLICATION_JSON));

        AiAnalysisProperties properties = new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        McpAnalysisProperties mcpProperties = new McpAnalysisProperties(
                "https://mcp.test/mcp", "mcp-secret"
        );
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties, mcpProperties, new ObjectMapper()
        );

        AiAnalysisResponse response = client.analyze(new AiAnalysisPrompt(
                "prompt", LocalDate.of(2026, 8, 18)
        ));

        assertThat(response.reportName()).isEqualTo("입지 분석");
        assertThat(response.analysisBasisDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        server.verify();
    }

    /**
     * output_text 없이 steps의 model_output 텍스트에 담긴 분석 JSON에서도 보고서명을 읽을 수 있는지 검증한다.
     */
    @Test
    void extractsModelOutputStep() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andRespond(withSuccess(
                        """
                                {"status":"completed","steps":[{"type":"model_output","content":[
                                  {"type":"text","text":"{\\"reportName\\":\\"입지 분석\\",\\"overallLocationEvaluation\\":{\\"grade\\":\\"INSUFFICIENT_DATA\\"}}"}
                                ]}]}
                                """,
                        MediaType.APPLICATION_JSON
                ));

        AiAnalysisProperties properties = new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        McpAnalysisProperties mcpProperties = new McpAnalysisProperties(
                "https://mcp.test/mcp", ""
        );
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties, mcpProperties, new ObjectMapper()
        );

        assertThat(client.analyze(new AiAnalysisPrompt(
                "prompt", LocalDate.of(2026, 8, 18)
        )).reportName()).isEqualTo("입지 분석");
        server.verify();
    }

    /**
     * MCP 토큰이 빈 문자열이면 도구 설정의 headers 필드 자체를 보내지 않는지 검증한다.
     */
    @Test
    void omitsBlankMcpAuthorization() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andExpect(jsonPath("$.tools[0].headers").doesNotExist())
                .andRespond(withSuccess(interactionResponse(), MediaType.APPLICATION_JSON));

        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties(), new McpAnalysisProperties("https://mcp.test/mcp", ""), new ObjectMapper()
        );

        client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18)));

        server.verify();
    }

    /**
     * Gemini API 키가 없으면 분석 요청에서 AI_SERVICE_UNAVAILABLE로 실패하는지 검증한다.
     */
    @Test
    void rejectsMissingGeminiApiKey() {
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                RestClient.create(),
                new AiAnalysisProperties("gemini", "http://gemini.test/v1beta", null, "", Duration.ZERO, Duration.ZERO),
                new McpAnalysisProperties("https://mcp.test/mcp", ""),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    /**
     * MCP 서버 URL이 없으면 분석 요청에서 MCP_SERVICE_UNAVAILABLE로 실패하는지 검증한다.
     */
    @Test
    void rejectsMissingMcpServerUrl() {
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                RestClient.create(), properties(), new McpAnalysisProperties("", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE);
    }

    /**
     * Gemini HTTP 400 응답을 AI_SERVICE_UNAVAILABLE 도메인 오류로 변환하는지 검증한다.
     */
    @Test
    void mapsGeminiBadRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andRespond(withBadRequest().body("invalid Gemini request"));
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties(), new McpAnalysisProperties("https://mcp.test/mcp", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE);

        server.verify();
    }

    /**
     * HTTP 성공이어도 interaction 상태가 failed이면 AI_SERVICE_UNAVAILABLE로 처리하는지 검증한다.
     */
    @Test
    void mapsFailedGeminiInteraction() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andRespond(withSuccess("{\"status\":\"failed\"}", MediaType.APPLICATION_JSON));
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties(), new McpAnalysisProperties("https://mcp.test/mcp", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE);

        server.verify();
    }

    /**
     * 완료 출력이 구조화된 분석 대신 html 필드를 반환하면 AI_RESPONSE_INVALID로 거절하는지 검증한다.
     */
    @Test
    void rejectsDirectGeminiHtml() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output_text\":\"{\\\"html\\\":\\\"<main/>\\\"}\"}",
                        MediaType.APPLICATION_JSON));
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties(), new McpAnalysisProperties("https://mcp.test/mcp", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_RESPONSE_INVALID);

        server.verify();
    }

    /**
     * 완료 상태라도 텍스트 출력 없이 빈 steps만 있으면 AI_RESPONSE_INVALID인지 검증한다.
     */
    @Test
    void rejectsMissingInteractionText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta/");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/interactions?key=test-key"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"steps\":[]}", MediaType.APPLICATION_JSON));
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties(), new McpAnalysisProperties("https://mcp.test/mcp", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_RESPONSE_INVALID);

        server.verify();
    }

    /**
     * 가짜 Gemini 주소·키와 짧은 제한 시간을 사용해 HTTP 대역 검증에 필요한 설정을 제공한다.
     */
    private AiAnalysisProperties properties() {
        return new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
    }

    /**
     * output_text에 최소 분석 JSON이 직렬화된 완료 interaction 응답을 제공해 기본 파싱 경로를 재현한다.
     */
    private String interactionResponse() {
        return "{\"status\":\"completed\",\"output_text\":"
                + "\"{\\\"reportName\\\":\\\"입지 분석\\\","
                + "\\\"overallLocationEvaluation\\\":{\\\"grade\\\":\\\"INSUFFICIENT_DATA\\\"}}\"}";
    }
}
