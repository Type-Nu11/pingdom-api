package com.typenull.pingdom.analysis.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LocationAnalysisPromptFactoryTest {

    private final LocationAnalysisPromptFactory factory = new LocationAnalysisPromptFactory(new ObjectMapper());

    @Test
    void buildsPromptWithRequiredRegionAndArbitraryAdditionalCriteria() {
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("서울 강남구");
        request.setDesiredIndustry("카페");
        request.addAdditionalCriterion("budget", 100000000);

        AiAnalysisPrompt prompt = factory.create(request, LocalDate.of(2026, 8, 18));

        assertThat(prompt.content()).contains("MCP의 읽기 전용 도구");
        assertThat(prompt.content()).contains("서울 강남구", "카페");
        assertThat(prompt.content()).contains("종합 입지 평가", "주변 시설");
        assertThat(prompt.content()).contains(
                "analysisScope", "dataSources", "recommendedPlaces", "derivedFromPlace", "반경",
                "FRONTEND_REQUEST_JSON_BEGIN", "FRONTEND_REQUEST_JSON_END",
                "totalScore >= 70", "totalScore가 45~69"
        );
    }

    @Test
    void limitsAiCriteriaToRegionAndIndustry() throws Exception {
        LocationAnalysisRequest request = new ObjectMapper().readValue(
                "{\"region\":\"부산 해운대구\",\"targetAge\":\"20-39\",\"monthlyBudget\":5000000}",
                LocationAnalysisRequest.class
        );

        assertThat(request.toCriteriaMap())
                .containsEntry("region", "부산 해운대구")
                .doesNotContainKeys("targetAge", "targetGender", "monthlyBudget");
    }
}
