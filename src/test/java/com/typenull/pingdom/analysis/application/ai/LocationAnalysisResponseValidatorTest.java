package com.typenull.pingdom.analysis.application.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisResponseValidatorTest {

    private final LocationAnalysisResponseValidator validator = new LocationAnalysisResponseValidator();

    @Test
    void rejectsSuitableGradeWhenCoreDataIsMissing() {
        LocationAnalysisRequest request = request();
        LocationAnalysisContent content = content(LocationAnalysisContent.Grade.SUITABLE, List.of());

        assertThatThrownBy(() -> validator.validate(request, new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class);
    }

    @Test
    void acceptsInsufficientDataWithLimitation() {
        LocationAnalysisRequest request = request();
        LocationAnalysisContent content = content(
                LocationAnalysisContent.Grade.INSUFFICIENT_DATA,
                List.of("MCP 데이터 없음")
        );

        validator.validate(request, new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18)));
    }

    @Test
    void acceptsSuitableGradeOnlyWhenRecommendedPlaceAndDerivedTrafficDataExist() {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석",
                new LocationAnalysisContent.OverallLocationEvaluation(
                        LocationAnalysisContent.Grade.SUITABLE, "분석", List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "분석", "추천 장소 A", List.of(metric("20-29")), List.of(metric("여성")), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "분석", 100d, List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                List.of(new LocationAnalysisContent.RecommendedPlace(
                        1, "추천 장소 A", "대구 북구 주소", 80d, "유동인구가 많음", List.of()
                )),
                new LocationAnalysisContent.AnalysisScope(
                        "대구광역시 북구", "대구광역시 북구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                        "구 전체", null
                ),
                List.of(), List.of()
        );

        validator.validate(request(), new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18)));
    }

    private LocationAnalysisContent.Metric metric(String label) {
        return new LocationAnalysisContent.Metric(label, 10d, "PEOPLE", 10d);
    }

    private LocationAnalysisRequest request() {
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("대구광역시 북구");
        return request;
    }

    private LocationAnalysisContent content(LocationAnalysisContent.Grade grade, List<String> limitations) {
        return new LocationAnalysisContent(
                "입지 분석",
                new LocationAnalysisContent.OverallLocationEvaluation(grade, "데이터 없음", List.of(), List.of(), List.of()),
                new LocationAnalysisContent.TargetPopulationAnalysis("데이터 없음", List.of(), List.of(), List.of()),
                new LocationAnalysisContent.FootTrafficAnalysis("데이터 없음", null, List.of(), List.of(), List.of()),
                new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                new LocationAnalysisContent.AnalysisScope(
                        "대구광역시 북구", "대구광역시 북구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                        "구 전체", null
                ),
                List.of(),
                limitations
        );
    }
}
