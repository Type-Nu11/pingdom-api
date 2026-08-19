package com.typenull.pingdom.analysis.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisDataProvider;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
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
        payload.put("business", criteria.get("category"));
        payload.put("region", criteria.get("region"));
        payload.put("message", buildMessage(criteria));
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

    private String buildMessage(Map<String, Object> criteria) {
        return "업종: %s\n지역: %s\n주요 고객층: %s\n주요 영업 시간대: %s"
                .formatted(
                        value(criteria.get("category")),
                        value(criteria.get("region")),
                        value(criteria.get("targetCustomerGroup")),
                        value(criteria.get("operatingHours"))
                );
    }

    private String value(Object value) {
        if (value == null) {
            return "미지정";
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "미지정";
    }
}
