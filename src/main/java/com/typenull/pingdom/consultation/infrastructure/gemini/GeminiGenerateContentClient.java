package com.typenull.pingdom.consultation.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.application.GeminiIntroClient;
import com.typenull.pingdom.consultation.application.GeminiVoiceClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Gemini 첫 후보의 텍스트 조각을 안내 문구 또는 ProviderEnvelope JSON으로 읽는 HTTP 어댑터.
 * 안내 문구 누락은 빈 Optional, JSON 해석 실패는 null로 표현하고 전송 예외 및 최종 스키마 검증은 호출 서비스가 처리.
 */
@Component
public class GeminiGenerateContentClient implements GeminiIntroClient, GeminiVoiceClient {

    private static final int MAX_OUTPUT_TOKENS = 80;
    private static final int CANDIDATE_COUNT = 1;
    private static final String SYSTEM_INSTRUCTION = """
            사용자의 첫 상담 질문에 한국어로 짧고 공감하는 안내를 작성하세요.
            업종 카테고리를 선택하도록 자연스럽게 안내하고, 두 문장을 넘기지 마세요.
            질문에 포함되지 않은 개인정보나 이전 상담 내용은 추정하거나 언급하지 마세요.
            """;
    private static final String VOICE_SYSTEM_INSTRUCTION = """
            Return exactly one JSON object for Pingdom ProviderEnvelope v1. Never use markdown.
            Allowed output only: schemaVersion=1, id, and one of assistant_message,
            clarification_request, command_request, protocol_error. Never emit command_result,
            source=app, credentials, location coordinates, reservation confirmation, or provider details.
            If a request is ambiguous, use clarification_request. If no safe command applies, use assistant_message.
            The envelope id must equal the supplied request id.
            """;

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;

    public GeminiGenerateContentClient(RestClient geminiRestClient, GeminiProperties properties) {
        this.geminiRestClient = geminiRestClient;
        this.properties = properties;
    }

    @Override
    public Optional<String> generateIntro(String message) {
        JsonNode response = geminiRestClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", properties.apiKey())
                .body(new GenerateContentRequest(
                        List.of(new Content(List.of(new Part(message)))),
                        new Content(List.of(new Part(SYSTEM_INSTRUCTION))),
                        new GenerationConfig(MAX_OUTPUT_TOKENS, CANDIDATE_COUNT, null)
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("candidates").isArray() || response.path("candidates").isEmpty()) {
            return Optional.empty();
        }

        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return Optional.empty();
        }

        String text = StreamSupport.stream(parts.spliterator(), false)
                .map(part -> part.path("text").asText())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
    }

    @Override
    public JsonNode generateEnvelope(String message, String requestId) {
        JsonNode response = geminiRestClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", properties.apiKey())
                .body(new GenerateContentRequest(
                        List.of(new Content(List.of(new Part(message)))),
                        new Content(List.of(new Part(VOICE_SYSTEM_INSTRUCTION + "\\nrequest id: " + requestId))),
                        new GenerationConfig(512, CANDIDATE_COUNT, MediaType.APPLICATION_JSON_VALUE)
                ))
                .retrieve()
                .body(JsonNode.class);

        String text = extractText(response).orElse("");
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
        } catch (Exception exception) {
            return null;
        }
    }

    private Optional<String> extractText(JsonNode response) {
        if (response == null || !response.path("candidates").isArray() || response.path("candidates").isEmpty()) {
            return Optional.empty();
        }
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return Optional.empty();
        }
        String text = StreamSupport.stream(parts.spliterator(), false)
                .map(part -> part.path("text").asText())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + right)
                .orElse("");
        return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
    }

    private record GenerateContentRequest(
            List<Content> contents,
            Content systemInstruction,
            GenerationConfig generationConfig
    ) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GenerationConfig(int maxOutputTokens, int candidateCount, String responseMimeType) {
    }
}
