package com.typenull.pingdom.analysis.application.ai;

import java.util.List;
import java.util.Map;

/** Gemini의 tool call을 Pingdom MCP 서버의 JSON-RPC 도구 호출로 중계한다. */
public interface McpAnalysisClient {

    List<McpTool> listTools();

    McpToolResult callTool(String name, Map<String, Object> arguments);

    record McpTool(String name, String description, Map<String, Object> inputSchema) {
    }

    record McpToolResult(String name, String content, boolean isError) {
    }
}
