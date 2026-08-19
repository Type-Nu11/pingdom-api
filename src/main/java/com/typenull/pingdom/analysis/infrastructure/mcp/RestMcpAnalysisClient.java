package com.typenull.pingdom.analysis.infrastructure.mcp;

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
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Pingdom MCP의 JSON-RPC /mcp 엔드포인트를 호출하는 MCP Client다. */
@RequiredArgsConstructor
public class RestMcpAnalysisClient implements McpAnalysisClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestId = new AtomicLong();

    @Override
    public List<McpTool> listTools() {
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
        String content = result.path("content").isArray()
                ? result.path("content").toString()
                : result.toString();
        return new McpToolResult(name, content, result.path("isError").asBoolean(false));
    }

    private JsonNode request(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", requestId.incrementAndGet());
        payload.put("method", method);
        payload.put("params", params);
        try {
            JsonNode response = restClient.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.has("error")) {
                throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, exception);
        }
    }
}
