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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gemini tool call과 Pingdom MCP tool 실행을 중계하는 클라이언트다. */
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    /** recommend_location이 내부에서 반경 확장을 처리하므로 요청당 MCP 실행은 한 번으로 제한한다. */
    private static final int MAX_TOOL_CALLS = 1;
    private static final String DESIGN_REFERENCE_RESOURCE = "analysis/design-reference.png";
    private static final Logger log = LoggerFactory.getLogger(GeminiAiAnalysisClient.class);

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
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode userContent = contents.addObject().put("role", "user");
        ArrayNode promptParts = userContent.putArray("parts");
        promptParts.addObject().put("text", prompt.content());
        appendDesignReference(promptParts);

        for (int toolCallCount = 0; toolCallCount <= MAX_TOOL_CALLS; toolCallCount++) {
            JsonNode response = generateContent(contents, mcpTools);
            JsonNode modelContent = response.path("candidates").path(0).path("content");
            JsonNode functionCall = findFunctionCall(modelContent);
            if (functionCall == null) {
                return parseFinalResponse(extractText(response), prompt);
            }
            if (toolCallCount == MAX_TOOL_CALLS) {
                contents.addObject()
                        .put("role", "user")
                        .putArray("parts")
                        .addObject()
                        .put("text", "도구 호출을 중단하고 지금까지 조회된 결과만 사용해 최종 JSON 보고서를 반환하라. 추가 도구를 호출하지 마라.");
                JsonNode finalResponse = generateContent(contents, List.of());
                return parseFinalResponse(extractText(finalResponse), prompt);
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

            contents.add(modelContent.deepCopy());
            McpAnalysisClient.McpToolResult toolResult = mcpAnalysisClient.callTool(name, arguments);
            appendFunctionResponse(contents, toolResult);
        }
        throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
    }

    private void appendDesignReference(ArrayNode parts) {
        ClassPathResource resource = new ClassPathResource(DESIGN_REFERENCE_RESOURCE);
        if (!resource.exists()) {
            log.warn("Gemini 디자인 레퍼런스를 찾지 못했습니다. resource={}", DESIGN_REFERENCE_RESOURCE);
            return;
        }
        try (var inputStream = resource.getInputStream()) {
            byte[] image = inputStream.readAllBytes();
            parts.addObject()
                    .putObject("inline_data")
                    .put("mime_type", "image/png")
                    .put("data", Base64.getEncoder().encodeToString(image));
        } catch (IOException exception) {
            log.warn("Gemini 디자인 레퍼런스를 읽지 못했습니다. resource={}", DESIGN_REFERENCE_RESOURCE, exception);
        }
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
