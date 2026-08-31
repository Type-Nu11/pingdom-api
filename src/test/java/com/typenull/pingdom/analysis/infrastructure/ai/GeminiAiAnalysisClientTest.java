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

    @Test
    void registersRemoteMcpAndParsesFinalInteractionOutput() {
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

    @Test
    void extractsTextFromModelOutputStepWhenOutputTextIsAbsent() {
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

    @Test
    void omitsMcpAuthorizationHeaderWhenAuthTokenIsBlank() {
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

    @Test
    void failsBeforeRemoteCallWhenGeminiApiKeyIsMissing() {
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

    @Test
    void failsBeforeRemoteCallWhenMcpServerUrlIsMissing() {
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                RestClient.create(), properties(), new McpAnalysisProperties("", ""), new ObjectMapper()
        );

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt("prompt", LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsGeminiBadRequestToAiServiceUnavailable() {
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

    @Test
    void mapsFailedGeminiInteractionToAiServiceUnavailable() {
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

    @Test
    void rejectsHtmlReturnedDirectlyByGemini() {
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

    @Test
    void rejectsCompletedInteractionWithoutTextOutput() {
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

    private AiAnalysisProperties properties() {
        return new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
    }

    private String interactionResponse() {
        return "{\"status\":\"completed\",\"output_text\":"
                + "\"{\\\"reportName\\\":\\\"입지 분석\\\","
                + "\\\"overallLocationEvaluation\\\":{\\\"grade\\\":\\\"INSUFFICIENT_DATA\\\"}}\"}";
    }
}
