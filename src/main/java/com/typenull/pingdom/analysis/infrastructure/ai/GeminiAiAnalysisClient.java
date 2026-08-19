package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Google Gemini Developer API의 단일 JSON 생성 요청을 분석 포트에 연결한다. */
@RequiredArgsConstructor
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    private final RestClient restClient;
    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, null);
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new GeminiGenerateRequest(
                            List.of(new GeminiContent(List.of(new GeminiPart(prompt.content())))),
                            new GeminiGenerationConfig("application/json", 0.2)
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, exception);
        }

        String json = extractText(response);
        if (!StringUtils.hasText(json)) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
        try {
            String normalizedJson = normalizeJson(json);
            JsonNode payload = objectMapper.readTree(normalizedJson);
            if (payload.has("html")) {
                return new AiAnalysisResponse(
                        payload.path("reportName").asText(null),
                        payload.path("html").asText(null),
                        prompt.analysisBasisDate()
                );
            }
            LocationAnalysisContent content = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .forType(LocationAnalysisContent.class)
                    .readValue(normalizedJson);
            return new AiAnalysisResponse(content, prompt.analysisBasisDate());
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private String extractText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (part.path("text").isTextual()) {
                content.append(part.get("text").asText());
            }
        }
        return content.toString();
    }

    private String normalizeJson(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd < 0
                    ? normalized.substring(3, normalized.length() - 3).trim()
                    : normalized.substring(firstLineEnd + 1, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private record GeminiGenerateRequest(
            List<GeminiContent> contents,
            GeminiGenerationConfig generationConfig
    ) {
    }

    private record GeminiContent(List<GeminiPart> parts) {
    }

    private record GeminiPart(String text) {
    }

    private record GeminiGenerationConfig(
            String responseMimeType,
            double temperature
    ) {
    }
}
