package com.typenull.pingdom.analysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaAiAnalysisClientTest {

    private final AiAnalysisProperties properties = new AiAnalysisProperties(
            "ollama", "http://ollama.test", "qwen2.5:7b", Duration.ofSeconds(1), Duration.ofSeconds(2)
    );

    @Test
    void sendsSystemAndUserPromptToOllamaAndReturnsHtml() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("user"))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"```html\\n<h2>분석</h2>\\n```\"}}",
                        MediaType.APPLICATION_JSON
                ));

        OllamaAiAnalysisClient client = new OllamaAiAnalysisClient(builder.build(), properties);

        AiAnalysisResponse response = client.analyze(new AiAnalysisPrompt(
                "user", LocalDate.of(2026, 8, 18)
        ));

        assertThat(response.reportName()).isEqualTo("입지 분석 보고서");
        assertThat(response.analysisBasisDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(response.html()).isEqualTo("<h2>분석</h2>");
        server.verify();
    }

    @Test
    void rejectsEmptyOllamaContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\" \"}}", MediaType.APPLICATION_JSON));
        OllamaAiAnalysisClient client = new OllamaAiAnalysisClient(builder.build(), properties);

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt(
                "user", LocalDate.of(2026, 8, 18)
        )))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_RESPONSE_INVALID);
        server.verify();
    }
}
