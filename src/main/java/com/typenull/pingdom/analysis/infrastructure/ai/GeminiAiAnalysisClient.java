package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisClient;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gemini tool call과 Pingdom MCP tool 실행을 중계하는 클라이언트다. */
@Slf4j
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    /** recommend_location이 내부에서 반경 확장을 처리하므로 요청당 MCP 실행은 한 번으로 제한한다. */
    private static final int MAX_TOOL_CALLS = 1;

    private final RestClient restClient;
    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final McpAnalysisClient mcpAnalysisClient;

    public GeminiAiAnalysisClient(
            RestClient restClient,
            AiAnalysisProperties properties,
            ObjectMapper objectMapper
    ) {
        this(restClient, properties, objectMapper, new DisabledMcpAnalysisClient());
    }

    public GeminiAiAnalysisClient(
            RestClient restClient,
            AiAnalysisProperties properties,
            ObjectMapper objectMapper,
            McpAnalysisClient mcpAnalysisClient
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mcpAnalysisClient = mcpAnalysisClient;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, null);
        }

        List<McpAnalysisClient.McpTool> mcpTools = mcpAnalysisClient.listTools();
        log.info("입지 분석 AI 시작. mcpToolCount={}, requestedRegionPresent={}",
                mcpTools.size(), StringUtils.hasText(prompt.requestedRegion()));
        ArrayNode contents = objectMapper.createArrayNode();
        contents.addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", prompt.content());

        for (int toolCallCount = 0; toolCallCount <= MAX_TOOL_CALLS; toolCallCount++) {
            JsonNode response = generateContent(contents, mcpTools);
            JsonNode modelContent = response.path("candidates").path(0).path("content");
            JsonNode functionCall = findFunctionCall(modelContent);
            if (functionCall == null) {
                return parseFinalResponseWithRepair(contents, response, prompt);
            }
            if (toolCallCount == MAX_TOOL_CALLS) {
                contents.addObject()
                        .put("role", "user")
                        .putArray("parts")
                        .addObject()
                        .put("text", "도구 호출을 중단하고 지금까지 조회된 결과만 사용해 최종 JSON 보고서를 반환하라. 추가 도구를 호출하지 마라.");
                JsonNode finalResponse = generateContent(contents, List.of());
                return parseFinalResponseWithRepair(contents, finalResponse, prompt);
            }

            String name = functionCall.path("name").asText(null);
            Map<String, Object> convertedArguments = objectMapper.convertValue(
                    functionCall.path("args"), new TypeReference<>() {
                    }
            );
            Map<String, Object> arguments = convertedArguments == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(convertedArguments);
            if (!StringUtils.hasText(name)) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            if ("recommend_location".equals(name) && StringUtils.hasText(prompt.requestedRegion())) {
                arguments.put("region", prompt.requestedRegion());
            }

            log.info("입지 분석 MCP 호출. tool={}, argumentKeys={}", name, arguments.keySet());
            contents.add(modelContent.deepCopy());
            McpAnalysisClient.McpToolResult toolResult = mcpAnalysisClient.callTool(name, arguments);
            log.info("입지 분석 MCP 응답 수신. tool={}, isError={}, contentLength={}",
                    name, toolResult.isError(), toolResult.content() == null ? 0 : toolResult.content().length());
            appendFunctionResponse(contents, toolResult);
        }
        throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
    }

    private JsonNode generateContent(ArrayNode contents, List<McpAnalysisClient.McpTool> mcpTools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("contents", contents);
        if (!mcpTools.isEmpty()) {
            ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (McpAnalysisClient.McpTool tool : mcpTools) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", tool.name());
                declaration.put("description", tool.description());
                declaration.set("parameters", objectMapper.valueToTree(tool.inputSchema()));
            }
        }
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);

        try {
            JsonNode response = restClient.post()
                    .uri("models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, exception);
        }
    }

    private JsonNode findFunctionCall(JsonNode modelContent) {
        for (JsonNode part : modelContent.path("parts")) {
            if (part.path("functionCall").isObject()) {
                return part.path("functionCall");
            }
        }
        return null;
    }

    private void appendFunctionResponse(
            ArrayNode contents,
            McpAnalysisClient.McpToolResult toolResult
    ) {
        ObjectNode content = contents.addObject().put("role", "user");
        ObjectNode part = content.putArray("parts").addObject();
        ObjectNode functionResponse = part.putObject("functionResponse");
        functionResponse.put("name", toolResult.name());
        ObjectNode response = functionResponse.putObject("response");
        try {
            response.set("result", objectMapper.readTree(toolResult.content()));
        } catch (JsonProcessingException exception) {
            response.put("result", toolResult.content());
        }
        response.put("isError", toolResult.isError());
    }

    private AiAnalysisResponse parseFinalResponse(String content, AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(content)) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
        try {
            String normalizedJson = normalizeJson(content);
            JsonNode payload = objectMapper.readTree(normalizedJson);
            if (payload.has("html")) {
                // AI가 임의 HTML을 반환하면 보고서마다 디자인과 한글 폰트가 달라질 수 있으므로
                // 고정 XHTML 템플릿이 사용할 구조화 JSON만 허용한다.
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            LocationAnalysisContent structured = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .forType(LocationAnalysisContent.class)
                    .readValue(normalizedJson);
            return new AiAnalysisResponse(structured, prompt.analysisBasisDate());
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    /** 계약 오류는 한 번만 수정 요청한다. 두 번째 실패는 원본 오류로 클라이언트에 502를 반환한다. */
    private AiAnalysisResponse parseFinalResponseWithRepair(
            ArrayNode previousContents,
            JsonNode response,
            AiAnalysisPrompt prompt
    ) {
        String content = extractText(response);
        try {
            return parseFinalResponse(content, prompt);
        } catch (AnalysisReportException exception) {
            log.warn("입지 분석 AI JSON 계약 오류. retry=true, cause={}, causeMessage={}, responsePreview={}",
                    exception.getCause() == null ? "validation" : exception.getCause().getClass().getSimpleName(),
                    causeMessage(exception),
                    responsePreview(content));
            ArrayNode repairContents = previousContents.deepCopy();
            JsonNode modelContent = response.path("candidates").path(0).path("content");
            if (modelContent.isObject()) {
                repairContents.add(modelContent.deepCopy());
            }
            repairContents.addObject()
                    .put("role", "user")
                    .putArray("parts")
                    .addObject()
                    .put("text", "직전 JSON은 서버 계약을 지키지 못했다. 필드명·enum·중첩 구조를 유지하고, "
                            + "evidences에는 문자열이 아닌 Evidence 객체만 넣어 유효한 JSON 객체만 다시 반환하라. "
                            + "MCP에서 받은 실제 수치는 삭제하거나 0으로 바꾸지 마라.");

            JsonNode repairedResponse = generateContent(repairContents, List.of());
            String repairedContent = extractText(repairedResponse);
            try {
                AiAnalysisResponse repaired = parseFinalResponse(repairedContent, prompt);
                log.info("입지 분석 AI JSON 계약 수정 재시도 성공. responseLength={}", repairedContent.length());
                return repaired;
            } catch (AnalysisReportException retryException) {
                log.warn("입지 분석 AI JSON 계약 수정 재시도 실패. cause={}, causeMessage={}, responsePreview={}",
                        retryException.getCause() == null ? "validation" : retryException.getCause().getClass().getSimpleName(),
                        causeMessage(retryException),
                        responsePreview(repairedContent));
                throw retryException;
            }
        }
    }

    private String responsePreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "<empty>";
        }
        String normalized = content.replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= 700 ? normalized : normalized.substring(0, 700) + "...";
    }

    private String causeMessage(AnalysisReportException exception) {
        Throwable cause = exception.getCause();
        if (cause == null || !StringUtils.hasText(cause.getMessage())) {
            return "<none>";
        }
        String normalized = cause.getMessage().replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private String extractText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (part.path("text").isTextual()) {
                content.append(part.path("text").asText());
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
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart > 0 || (objectEnd >= 0 && objectEnd < normalized.length() - 1)) {
            if (objectStart < 0 || objectEnd < objectStart) {
                return normalized;
            }
            normalized = normalized.substring(objectStart, objectEnd + 1);
        }
        return normalized;
    }

    private static final class DisabledMcpAnalysisClient implements McpAnalysisClient {

        @Override
        public List<McpTool> listTools() {
            return List.of();
        }

        @Override
        public McpToolResult callTool(String name, Map<String, Object> arguments) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
        }
    }
}
