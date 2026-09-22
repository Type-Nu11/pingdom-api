package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * 비스트리밍 /api/chat 요청에 단일 user 프롬프트와 모델을 전달하고 분석 JSON의 이름·기준일·데이터 부족 등급을 복원하는지 검증.
     */
    @Test
    void sendsAndParsesOllamaAnalysis() {
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
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"reportName\\\":\\\"입지 분석\\\",\\\"overallLocationEvaluation\\\":{\\\"grade\\\":\\\"INSUFFICIENT_DATA\\\",\\\"summary\\\":\\\"데이터 없음\\\",\\\"strengths\\\":[],\\\"risks\\\":[],\\\"evidences\\\":[]},\\\"targetPopulationAnalysis\\\":{\\\"summary\\\":\\\"데이터 없음\\\",\\\"age\\\":[],\\\"gender\\\":[],\\\"evidences\\\":[]},\\\"footTrafficAnalysis\\\":{\\\"summary\\\":\\\"데이터 없음\\\",\\\"total\\\":null,\\\"byTime\\\":[],\\\"byDay\\\":[],\\\"evidences\\\":[]},\\\"nearbyFacilities\\\":{\\\"competitors\\\":[],\\\"convenienceFacilities\\\":[],\\\"transportFacilities\\\":[],\\\"evidences\\\":[]},\\\"analysisScope\\\":{\\\"requestedRegion\\\":\\\"서울 강남구\\\",\\\"normalizedRegion\\\":\\\"서울특별시 강남구\\\",\\\"scopeLevel\\\":\\\"DISTRICT\\\",\\\"scopeDescription\\\":\\\"구 전체\\\",\\\"radiusMeters\\\":null},\\\"dataSources\\\":[],\\\"limitations\\\":[\\\"데이터 없음\\\"]}\"}}",
                        MediaType.APPLICATION_JSON
                ));

        OllamaAiAnalysisClient client = new OllamaAiAnalysisClient(builder.build(), properties, new ObjectMapper());

        AiAnalysisResponse response = client.analyze(new AiAnalysisPrompt(
                "user", LocalDate.of(2026, 8, 18)
        ));

        assertThat(response.reportName()).isEqualTo("입지 분석");
        assertThat(response.analysisBasisDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(response.content().overallLocationEvaluation().grade())
                .isEqualTo(com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent.Grade.INSUFFICIENT_DATA);
        server.verify();
    }

    /**
     * Ollama 응답 content가 공백뿐이면 AI_RESPONSE_INVALID로 처리하는지 검증.
     */
    @Test
    void rejectsEmptyOllamaContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"content\":\" \"}}", MediaType.APPLICATION_JSON));
        OllamaAiAnalysisClient client = new OllamaAiAnalysisClient(builder.build(), properties, new ObjectMapper());

        assertThatThrownBy(() -> client.analyze(new AiAnalysisPrompt(
                "user", LocalDate.of(2026, 8, 18)
        )))
                .isInstanceOf(AnalysisReportException.class)
                .extracting(exception -> ((AnalysisReportException) exception).getErrorCode())
                .isEqualTo(AnalysisReportErrorCode.AI_RESPONSE_INVALID);
        server.verify();
    }
}
