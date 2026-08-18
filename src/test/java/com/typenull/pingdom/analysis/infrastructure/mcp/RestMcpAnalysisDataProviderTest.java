package com.typenull.pingdom.analysis.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestMcpAnalysisDataProviderTest {

    @Test
    void sendsRecommendationCriteriaToPingdomMcp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/recommend"))
                .andExpect(jsonPath("$.business").value("카페"))
                .andExpect(jsonPath("$.region").value("서울 강남구"))
                .andRespond(withSuccess("{\"recommendations\":[{\"score\":0.9}]}", MediaType.APPLICATION_JSON));

        RestMcpAnalysisDataProvider provider = new RestMcpAnalysisDataProvider(
                builder.build(),
                new McpAnalysisProperties(true, "http://mcp.test", Duration.ofSeconds(1), Duration.ofSeconds(1))
        );

        String result = provider.fetch(Map.of(
                "desiredIndustry", "카페",
                "region", "서울 강남구",
                "targetAge", "20-39",
                "targetGender", "여성"
        ));

        assertThat(result).contains("recommendations", "0.9");
        server.verify();
    }
}
