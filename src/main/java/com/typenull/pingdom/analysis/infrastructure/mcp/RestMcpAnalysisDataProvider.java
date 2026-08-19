package com.typenull.pingdom.analysis.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisDataProvider;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** pingdom-mcp의 POST /recommend REST 엔드포인트를 호출한다. */
@RequiredArgsConstructor
public class RestMcpAnalysisDataProvider implements McpAnalysisDataProvider {

    private final RestClient restClient;
    private final McpAnalysisProperties properties;

    @Override
    public String fetch(Map<String, Object> criteria) {
        if (!properties.enabled()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", criteria.get("category"));
        payload.put("region", criteria.get("region"));
        payload.put("targetCustomerGroup", criteria.get("targetCustomerGroup"));
        payload.put("operatingHours", criteria.get("operatingHours"));
        Object additionalCriteria = criteria.get("additionalCriteria");
        if (additionalCriteria instanceof Map<?, ?> values && !values.isEmpty()) {
            payload.put("additionalCriteria", values);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
            }
            return response.toString();
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, exception);
        }
    }
}
