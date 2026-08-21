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
                "\"html\"",
                "additionalCriteria", "고정된 인라인 CSS",
                "완전한 단일 XHTML 문서", "<!DOCTYPE html>", "void element는 반드시 />로 닫는다",
                "sourceValues", "같은 기간·반경·집계 단위", "JSON 외의 문자"
        );
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
