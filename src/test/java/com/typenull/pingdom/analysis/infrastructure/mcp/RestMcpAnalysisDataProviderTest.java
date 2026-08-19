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
                .andExpect(jsonPath("$.category").value("카페"))
                .andExpect(jsonPath("$.region").value("서울 강남구"))
                .andExpect(jsonPath("$.targetCustomerGroup").value("20-39 여성"))
                .andExpect(jsonPath("$.operatingHours").value("18:00-22:00"))
                .andExpect(jsonPath("$.additionalCriteria.budget").value(5000000))
                .andRespond(withSuccess("{\"recommendations\":[{\"score\":0.9}]}", MediaType.APPLICATION_JSON));

        RestMcpAnalysisDataProvider provider = new RestMcpAnalysisDataProvider(
                builder.build(),
                new McpAnalysisProperties(true, "http://mcp.test", Duration.ofSeconds(1), Duration.ofSeconds(1))
        );

        String result = provider.fetch(Map.of(
                "category", "카페",
                "region", "서울 강남구",
                "targetCustomerGroup", "20-39 여성",
                "operatingHours", "18:00-22:00",
                "additionalCriteria", Map.of("budget", 5000000)
        ));

        assertThat(result).contains("recommendations", "0.9");
        server.verify();
    }
}
