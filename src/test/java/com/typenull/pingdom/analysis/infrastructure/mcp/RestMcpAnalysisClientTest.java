package com.typenull.pingdom.analysis.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestMcpAnalysisClientTest {

    @Test
    void initializesListsToolsAndCallsRecommendationTool() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://mcp.test/mcp");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mcp.test/mcp"))
                .andExpect(jsonPath("$.method").value("initialize"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}",
                        MediaType.APPLICATION_JSON
                ).header("Mcp-Session-Id", "session-1"));
        server.expect(requestTo("http://mcp.test/mcp"))
                .andExpect(jsonPath("$.method").value("tools/list"))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(header("MCP-Protocol-Version", "2025-06-18"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"recommend_location\",\"description\":\"추천\",\"inputSchema\":{\"type\":\"object\"}}]}}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo("http://mcp.test/mcp"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(header("MCP-Protocol-Version", "2025-06-18"))
                .andExpect(jsonPath("$.params.name").value("recommend_location"))
                .andExpect(jsonPath("$.params.arguments.region").value("대구 북구"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"recommendations\\\":[{\\\"rank\\\":1,\\\"metrics\\\":{\\\"total_foot\\\":2328}}]}\"}]}}",
                        MediaType.APPLICATION_JSON
                ));

        RestMcpAnalysisClient client = new RestMcpAnalysisClient(builder.build(), new ObjectMapper());

        assertThat(client.listTools()).extracting("name").containsExactly("recommend_location");
        assertThat(client.callTool("recommend_location", Map.of("region", "대구 북구")))
                .extracting("name", "content")
                .containsExactly("recommend_location", "{\"recommendations\":[{\"rank\":1,\"metrics\":{\"total_foot\":2328}}]}");
        server.verify();
    }
}
