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
        request.setCategory("카페");
        request.setTargetCustomerGroup("20-39 여성");
        request.setOperatingHours("18:00-22:00");
        request.addAdditionalCriterion("budget", 100000000);

        AiAnalysisPrompt prompt = factory.create(request, LocalDate.of(2026, 8, 18));

        assertThat(prompt.content()).contains(
                "Pingdom MCP",
                "읽기 전용 도구",
                "recommend_location을 정확히 한 번 호출"
        );
        assertThat(prompt.content()).contains("서울 강남구", "카페", "20-39 여성", "18:00-22:00");
        assertThat(prompt.requestedRegion()).isEqualTo("서울 강남구");
        assertThat(prompt.content()).contains(
                "recommendedPlaces", "derivedFromPlace", "반경",
                "FRONTEND_REQUEST_JSON_BEGIN", "FRONTEND_REQUEST_JSON_END",
                "SUITABLE", "CONDITIONAL", "INSUFFICIENT_DATA",
                "overallLocationEvaluation", "commercialAreaAnalysis", "competitionAnalysis",
                "businessPerformanceAnalysis", "dataQualityAnalysis", "analysisScope",
                "sourceValues", "JSON 외의 모든 문자는 출력하지 않는다"
        );
        assertThat(prompt.content()).contains(
                "metrics.total_foot",
                "metrics.avg_hour",
                "문자열이 아닌 객체",
                "임의의 0은 만들지 않는다"
        );
        assertThat(prompt.content()).doesNotContain("\"html\"");
    }

    @Test
    void limitsAiCriteriaToRegionAndIndustry() throws Exception {
        LocationAnalysisRequest request = new ObjectMapper().readValue(
                "{\"region\":\"부산 해운대구\",\"category\":\"카페\",\"targetCustomerGroup\":\"20-39 여성\",\"operatingHours\":\"18:00-22:00\",\"monthlyBudget\":5000000}",
                LocationAnalysisRequest.class
        );

        assertThat(request.toCriteriaMap())
                .containsEntry("region", "부산 해운대구")
                .containsEntry("category", "카페")
                .containsEntry("targetCustomerGroup", "20-39 여성")
                .containsEntry("operatingHours", "18:00-22:00")
                .extractingByKey("additionalCriteria")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("monthlyBudget", 5000000);
    }

}
