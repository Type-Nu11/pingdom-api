package com.typenull.pingdom.analysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAiAnalysisClientTest {

    @Test
    void sendsJsonGenerationRequestAndParsesCandidateText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("prompt"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(
                        responseJson(),
                        MediaType.APPLICATION_JSON
                ));

        AiAnalysisProperties properties = new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties, new ObjectMapper()
        );

        AiAnalysisResponse response = client.analyze(new AiAnalysisPrompt(
                "prompt", LocalDate.of(2026, 8, 18)
        ));

        assertThat(response.reportName()).isEqualTo("입지 분석");
        assertThat(response.htmlReport()).contains("<html>");
        server.verify();
    }

    @Test
    void executesGeminiToolCallThroughMcpAndSendsResultBack() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(jsonPath("$.tools[0].functionDeclarations[0].name").value("recommend_location"))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"recommend_location\",\"args\":{\"region\":\"대구 북구\",\"age_min\":20,\"age_max\":39,\"gender\":\"ANY\",\"radius_m\":1500}}}]}}]}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(jsonPath("$.contents.length()" ).value(3))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.name").value("recommend_location"))
                .andRespond(withSuccess(responseJson(), MediaType.APPLICATION_JSON));

        AiAnalysisProperties properties = new AiAnalysisProperties(
                "gemini", "http://gemini.test/v1beta", null, "test-key",
                Duration.ofSeconds(1), Duration.ofSeconds(2)
        );
        McpAnalysisClient mcpClient = new McpAnalysisClient() {
            @Override
            public List<McpTool> listTools() {
                return List.of(new McpTool(
                        "recommend_location", "추천 장소 조회", Map.of("type", "object")
                ));
            }

            @Override
            public McpToolResult callTool(String name, Map<String, Object> arguments) {
                return new McpToolResult(name, "{\"recommendations\":[]}", false);
            }
        };
        GeminiAiAnalysisClient client = new GeminiAiAnalysisClient(
                builder.build(), properties, new ObjectMapper(), mcpClient
        );

        AiAnalysisResponse response = client.analyze(new AiAnalysisPrompt(
                "prompt", LocalDate.of(2026, 8, 18)
        ));

        assertThat(response.reportName()).isEqualTo("입지 분석");
        server.verify();
    }

    private String responseJson() {
        try {
            String content = new ObjectMapper().writeValueAsString(Map.of(
                    "reportName", "입지 분석",
                    "html", "<!doctype html><html><body>보고서</body></html>"
            ));
            return new ObjectMapper().writeValueAsString(Map.of(
                    "candidates", List.of(Map.of(
                            "content", Map.of("parts", List.of(Map.of("text", content)))
                    ))
            ));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
