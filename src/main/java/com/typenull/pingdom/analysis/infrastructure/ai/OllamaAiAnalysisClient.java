package com.typenull.pingdom.analysis.infrastructure.ai;

import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.util.StringUtils;

/** Ollama의 로컬 /api/chat 엔드포인트를 보고서 AI 포트에 연결한다. */
@RequiredArgsConstructor
public class OllamaAiAnalysisClient implements AiAnalysisClient {

    private static final String REPORT_NAME = "입지 분석 보고서";

    private final RestClient restClient;
    private final AiAnalysisProperties properties;

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        OllamaChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaChatRequest(
                            properties.model(),
                            List.of(
                                    new OllamaMessage("system", prompt.systemInstruction()),
                                    new OllamaMessage("user", prompt.userPrompt())
                            ),
                            false
                    ))
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, exception);
        }

        String html = normalizeHtml(response == null || response.message() == null
                ? null
                : response.message().content());
        if (!StringUtils.hasText(html)) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
        return new AiAnalysisResponse(REPORT_NAME, prompt.analysisBasisDate(), html);
    }

    private String normalizeHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String normalized = content.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd < 0
                    ? normalized.substring(3, normalized.length() - 3).trim()
                    : normalized.substring(firstLineEnd + 1, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            boolean stream
    ) {
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }

    private record OllamaMessage(String role, String content) {
    }
}
