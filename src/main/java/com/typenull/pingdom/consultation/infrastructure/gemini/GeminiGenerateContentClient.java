package com.typenull.pingdom.consultation.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.application.GeminiIntroClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class GeminiGenerateContentClient implements GeminiIntroClient {

    private static final int MAX_OUTPUT_TOKENS = 80;
    private static final int CANDIDATE_COUNT = 1;
    private static final String SYSTEM_INSTRUCTION = """
            사용자의 첫 상담 질문에 한국어로 짧고 공감하는 안내를 작성하세요.
            업종 카테고리를 선택하도록 자연스럽게 안내하고, 두 문장을 넘기지 마세요.
            질문에 포함되지 않은 개인정보나 이전 상담 내용은 추정하거나 언급하지 마세요.
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
                        new GenerationConfig(MAX_OUTPUT_TOKENS, CANDIDATE_COUNT)
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

    private record GenerationConfig(int maxOutputTokens, int candidateCount) {
    }
}
