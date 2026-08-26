package com.typenull.pingdom.analysis.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisClient;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Pingdom MCP의 JSON-RPC /mcp 엔드포인트를 호출하는 MCP Client다. */
@RequiredArgsConstructor
@Slf4j
public class RestMcpAnalysisClient implements McpAnalysisClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestId = new AtomicLong();
    private final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private final ThreadLocal<String> protocolVersion = new ThreadLocal<>();

    @Override
    public List<McpTool> listTools() {
        // MCP Streamable HTTP는 initialize 응답의 세션을 같은 호출 흐름에서 재사용한다.
        sessionId.remove();
        protocolVersion.remove();
        request("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "pingdom-api", "version", "1.0")
        ));
        JsonNode response = request("tools/list", Map.of());
        JsonNode tools = response.path("result").path("tools");
        if (!tools.isArray()) {
            return List.of();
        }
        List<McpTool> result = new ArrayList<>();
        for (JsonNode tool : tools) {
            Map<String, Object> inputSchema = objectMapper.convertValue(
                    tool.path("inputSchema"), Map.class
            );
            result.add(new McpTool(
                    tool.path("name").asText(),
                    tool.path("description").asText(""),
                    inputSchema == null ? Map.of() : inputSchema
            ));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public McpToolResult callTool(String name, Map<String, Object> arguments) {
        JsonNode response = request("tools/call", Map.of(
                "name", name,
                "arguments", arguments == null ? Map.of() : arguments
        ));
        JsonNode result = response.path("result");
        String content = extractToolContent(result);
        logToolResult(name, result.path("isError").asBoolean(false), content);
        return new McpToolResult(name, content, result.path("isError").asBoolean(false));
    }

    /** MCP text content 내부의 JSON을 그대로 Gemini functionResponse에 전달한다. */
    private String extractToolContent(JsonNode result) {
        JsonNode content = result.path("content");
        if (!content.isArray()) {
            return result.toString();
        }
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.path("text").isTextual()) {
                return item.path("text").asText();
            }
        }
        return content.toString();
    }

    private void logToolResult(String name, boolean isError, String content) {
        try {
            JsonNode payload = objectMapper.readTree(content);
            JsonNode recommendations = payload.path("recommendations");
            JsonNode firstMetrics = recommendations.path(0).path("metrics");
            log.info("입지 분석 MCP 결과. tool={}, isError={}, recommendationCount={}, searchedRadiusMeters={}, "
                            + "firstTotalFootPresent={}, firstAgeMatchPresent={}, firstGenderMatchPresent={}",
                    name, isError, recommendations.isArray() ? recommendations.size() : 0,
                    payload.path("searched_radius_m").isNumber() ? payload.path("searched_radius_m").asInt() : null,
                    firstMetrics.hasNonNull("total_foot"), firstMetrics.hasNonNull("age_match"),
                    firstMetrics.hasNonNull("gender_match"));
        } catch (JsonProcessingException exception) {
            log.warn("입지 분석 MCP 결과가 JSON text가 아님. tool={}, isError={}, contentLength={}",
                    name, isError, content == null ? 0 : content.length());
        }
    }

    private JsonNode request(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", requestId.incrementAndGet());
        payload.put("method", method);
        payload.put("params", params);
        try {
            var request = restClient.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload);
            if (sessionId.get() != null) {
                request.header("Mcp-Session-Id", sessionId.get());
            }
            if (protocolVersion.get() != null) {
                request.header("MCP-Protocol-Version", protocolVersion.get());
            }
            ResponseEntity<JsonNode> entity = request.retrieve().toEntity(JsonNode.class);
            String issuedSessionId = entity.getHeaders().getFirst("Mcp-Session-Id");
            if (issuedSessionId != null) {
                sessionId.set(issuedSessionId);
            }
            JsonNode response = entity.getBody();
            String negotiatedVersion = response == null ? null
                    : response.path("result").path("protocolVersion").asText(null);
            if (negotiatedVersion != null) {
                protocolVersion.set(negotiatedVersion);
            }
            if (response == null || response.has("error")) {
                throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, exception);
        }
    }
}
