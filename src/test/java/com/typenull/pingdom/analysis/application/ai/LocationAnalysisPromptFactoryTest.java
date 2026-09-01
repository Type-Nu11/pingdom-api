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
                "Pingdom MCP 주소",
                "실행환경에 설정되어 있다",
                "읽기 전용 도구"
        );
        assertThat(prompt.content()).contains("서울 강남구", "카페", "20-39 여성", "18:00-22:00");
        assertThat(prompt.requestedRegion()).isEqualTo("서울 강남구");
        assertThat(prompt.content()).contains("종합 입지 평가", "주변 시설", "분석 범위", "데이터 출처");
        assertThat(prompt.content()).contains(
                "recommendedPlaces", "derivedFromPlace", "반경",
                "FRONTEND_REQUEST_JSON_BEGIN", "FRONTEND_REQUEST_JSON_END",
                "totalScore >= 70", "totalScore가 45~69",
                "additionalCriteria", "고정 디자인 XHTML",
                "7개 고정 섹션", "overallLocationEvaluation", "commercialAreaAnalysis",
                "overallScore",
                "competitionAnalysis", "businessPerformanceAnalysis", "dataQualityAnalysis", "통계 산출 근거",
                "sourceValues", "같은 기간·반경·집계 단위", "JSON 외의 문자",
                "SERVER_DESIGN_REFERENCE_BEGIN", "Pingdom Editorial Location Report v1",
                "#F8F7F2", "#7D8777", "수평 막대",
                "결과를 임의로 0, 빈 배열, \"데이터 없음\"으로 바꾸거나 무시하지 않는다",
                "자동 확장된 참고 분석 범위", "metrics.total_foot는 footTrafficAnalysis.total",
                "Evidence 객체 계약", "문자열이 아닌 아래 JSON 객체", "GEOCODE_FAILED: ..."
        );
        assertThat(prompt.content()).contains(
                "metrics.total_foot →",
                "metrics.avg_hour →",
                "문자열 배열을 반환하지 않는다",
                "임의 명칭·접미사를 만들거나"
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
