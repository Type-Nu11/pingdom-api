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
