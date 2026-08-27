package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.analysis.infrastructure.mcp.McpAnalysisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gemini Interactions API에 Remote MCP를 등록하고 최종 분석 결과만 받는 클라이언트다. */
@Slf4j
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    private static final String MCP_NAME = "pingdom_mcp";
    private static final String MCP_TOOL = "recommend_location";

    private final RestClient restClient;
    private final AiAnalysisProperties properties;
    private final McpAnalysisProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public GeminiAiAnalysisClient(
            RestClient restClient,
            AiAnalysisProperties properties,
            McpAnalysisProperties mcpProperties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, null);
        }
        if (!StringUtils.hasText(mcpProperties.serverUrl())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", properties.model());
        request.put("input", prompt.content());
        // recommend_location만 허용하므로 Gemini가 MCP를 반드시 한 번 호출하도록 강제한다.
        request.put("tool_choice", "any");
        request.set("tools", remoteMcpTools());

        log.info("입지 분석 Gemini Remote MCP 요청. model={}, mcpUrl={}, requestedRegionPresent={}",
                properties.model(), mcpProperties.serverUrl(), prompt.requestedRegion() != null);

        JsonNode response = requestInteractions(request);
        String output = extractOutputText(response);
        if (!StringUtils.hasText(output)) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
        return parseFinalResponse(output, prompt);
    }

    private ArrayNode remoteMcpTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode mcp = tools.addObject();
        mcp.put("type", "mcp_server");
        mcp.put("name", MCP_NAME);
        mcp.put("url", mcpProperties.serverUrl());
        mcp.putArray("allowed_tools").add(MCP_TOOL);
        if (StringUtils.hasText(mcpProperties.authToken())) {
            mcp.putObject("headers")
                    .put("Authorization", "Bearer " + mcpProperties.authToken());
        }
        return tools;
    }

    private JsonNode requestInteractions(ObjectNode request) {
        try {
            JsonNode response = restClient.post()
                    .uri("interactions")
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || "failed".equals(response.path("status").asText())) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, null);
            }
            return response;
        } catch (AnalysisReportException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, exception);
        }
    }

    /** Interactions 응답의 output_text 또는 model_output step에서 텍스트를 추출한다. */
    private String extractOutputText(JsonNode response) {
        if (response.path("output_text").isTextual()) {
            return response.path("output_text").asText();
        }
        StringBuilder output = new StringBuilder();
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if (content.path("text").isTextual()) {
                    output.append(content.path("text").asText());
                }
            }
        }
        return output.toString();
    }

    private AiAnalysisResponse parseFinalResponse(String content, AiAnalysisPrompt prompt) {
        try {
            String normalizedJson = normalizeJson(content);
            JsonNode payload = objectMapper.readTree(normalizedJson);
            if (payload.has("html")) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            LocationAnalysisContent structured = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .forType(LocationAnalysisContent.class)
                    .readValue(normalizedJson);
            return new AiAnalysisResponse(structured, prompt.analysisBasisDate());
        } catch (AnalysisReportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
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
        if (objectStart >= 0 && objectEnd >= objectStart
                && (objectStart > 0 || objectEnd < normalized.length() - 1)) {
            normalized = normalized.substring(objectStart, objectEnd + 1);
        }
        return normalized;
    }
}
