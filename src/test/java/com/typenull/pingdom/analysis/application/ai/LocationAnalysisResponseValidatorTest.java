package com.typenull.pingdom.analysis.application.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisResponseValidatorTest {

    private final LocationAnalysisResponseValidator validator = new LocationAnalysisResponseValidator();

    /**
     * script가 없는 기본 HTML 보고서는 validateHtml에서 예외 없이 통과하는지 검증.
     */
    @Test
    void acceptsSafeHtmlReport() {
        assertThatCode(() -> validator.validateHtml(new AiAnalysisResponse(
                "입지 분석",
                "<!doctype html><html><body>보고서</body></html>",
                LocalDate.of(2026, 8, 18)
        ))).doesNotThrowAnyException();
    }

    /**
     * script 태그가 포함된 보고서는 AnalysisReportException으로 거절되는지 검증.
     */
    @Test
    void rejectsHtmlReportWithScript() {
        assertThatThrownBy(() -> validator.validateHtml(new AiAnalysisResponse(
                "입지 분석",
                "<!doctype html><html><body><script>alert(1)</script></body></html>",
                LocalDate.of(2026, 8, 18)
        ))).isInstanceOf(AnalysisReportException.class);
    }

    /**
     * 추천 장소와 핵심 관측 데이터가 없는 보고서가 SUITABLE로 판정되면 검증을 거절하는지 확인.
     */
    @Test
    void rejectsUnsupportedSuitableGrade() {
        LocationAnalysisRequest request = request();
        LocationAnalysisContent content = content(LocationAnalysisContent.Grade.SUITABLE, List.of());

        assertThatThrownBy(() -> validator.validate(request, new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18))))
                .isInstanceOf(AnalysisReportException.class);
    }

    /**
     * 관측 데이터가 없는 보고서도 INSUFFICIENT_DATA 등급과 MCP 데이터 없음 한계를 명시하면 검증을 통과하는지 확인.
     */
    @Test
    void acceptsInsufficientDataWithLimitation() {
        LocationAnalysisRequest request = request();
        LocationAnalysisContent content = content(
                LocationAnalysisContent.Grade.INSUFFICIENT_DATA,
                List.of("MCP 데이터 없음")
        );

        validator.validate(request, new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18)));
    }

    /**
     * 추천 장소·연령/성별 지표·유동 총계·분석 섹션을 갖춘 SUITABLE 보고서가 예외 없이 통과하는지 검증.
     */
    @Test
    void acceptsSupportedSuitableGrade() {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석",
                new LocationAnalysisContent.OverallLocationEvaluation(
                        LocationAnalysisContent.Grade.SUITABLE, 80d, "분석", List.of(), List.of(), List.of()
                ),
                commercialArea(),
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "분석", "추천 장소 A", List.of(metric("20-29")), List.of(metric("여성")), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "분석", 100d, List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                competition(),
                businessPerformance(),
                dataQuality(),
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

    /**
     * 연령/성별 분포 없이 MCP 유동 총계와 추천 장소가 있으며 확장 반경·지역 밖 결과 한계를 명시한 CONDITIONAL 보고서를 허용하는지 검증.
     */
    @Test
    void acceptsLimitedTrafficConditionalGrade() {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석",
                new LocationAnalysisContent.OverallLocationEvaluation(
                        LocationAnalysisContent.Grade.CONDITIONAL, 60d, "확장 반경의 참고 분석 결과", List.of(), List.of(), List.of()
                ),
                commercialArea(),
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "타깃 일치 수만 제공됨", "추천 장소 A", List.of(), List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "MCP 유동인구 집계", 2328d, List.of(), List.of(), List.of(), "데이터 없음", null, List.of()
                ),
                new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                competition(),
                businessPerformance(),
                dataQuality(),
                List.of(new LocationAnalysisContent.RecommendedPlace(
                        1, "추천 장소 A", "서울특별시 송파구", 1d, "유동인구 2,328명", List.of()
                )),
                new LocationAnalysisContent.AnalysisScope(
                        "대구광역시 북구", "대구광역시 북구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                        "자동 확장된 참고 분석 범위", 15000d
                ),
                List.of(), List.of("요청 지역 밖 확장 반경 결과")
        );

        validator.validate(request(), new AiAnalysisResponse(content, LocalDate.of(2026, 8, 18)));
    }

    /**
     * 주어진 지표명에 관측 수 10과 비중 10%를 지정해 검증 가능한 타깃 분포 입력을 생성.
     */
    private LocationAnalysisContent.Metric metric(String label) {
        return new LocationAnalysisContent.Metric(label, 10d, "PEOPLE", 10d);
    }

    /**
     * 분석 범위 일치 검증에 사용할 대구광역시 북구 요청을 생성.
     */
    private LocationAnalysisRequest request() {
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("대구광역시 북구");
        return request;
    }

    /**
     * 추천·유동 데이터가 없는 보고서에 지정 등급과 한계 문구를 설정해 데이터 부족 정책의 입력을 구성.
     */
    private LocationAnalysisContent content(LocationAnalysisContent.Grade grade, List<String> limitations) {
        return new LocationAnalysisContent(
                "입지 분석",
                new LocationAnalysisContent.OverallLocationEvaluation(grade, "데이터 없음", List.of(), List.of(), List.of()),
                commercialArea(),
                new LocationAnalysisContent.TargetPopulationAnalysis("데이터 없음", List.of(), List.of(), List.of()),
                new LocationAnalysisContent.FootTrafficAnalysis("데이터 없음", null, List.of(), List.of(), List.of()),
                new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                competition(),
                businessPerformance(),
                dataQuality(),
                List.of(),
                new LocationAnalysisContent.AnalysisScope(
                        "대구광역시 북구", "대구광역시 북구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                        "구 전체", null
                ),
                List.of(),
                limitations
        );
    }

    /**
     * 대구 북구의 상권명·유형·요약을 갖춘 기본 상권 분석 섹션을 제공.
     */
    private LocationAnalysisContent.CommercialAreaAnalysis commercialArea() {
        return new LocationAnalysisContent.CommercialAreaAnalysis(
                "대구 북구 상권", "생활 상권", "상권 분석", List.of(), List.of()
        );
    }

    /**
     * 요약은 있으나 경쟁 수치와 목록은 없는 경쟁 분석 섹션을 제공.
     */
    private LocationAnalysisContent.CompetitionAnalysis competition() {
        return new LocationAnalysisContent.CompetitionAnalysis(
                "경쟁 분석", null, null, null, null, List.of(), List.of()
        );
    }

    /**
     * 요약과 빈 지표 목록을 가진 사업성 분석 섹션으로 구조적 필수 조건을 충족시킴.
     */
    private LocationAnalysisContent.BusinessPerformanceAnalysis businessPerformance() {
        return new LocationAnalysisContent.BusinessPerformanceAnalysis(
                "사업성 분석", List.of(), List.of(), List.of(), List.of()
        );
    }

    /**
     * 신뢰도 수치와 출처가 없는 상태를 데이터 없음 문구로 표현하는 품질 섹션을 제공.
     */
    private LocationAnalysisContent.DataQualityAnalysis dataQuality() {
        return new LocationAnalysisContent.DataQualityAnalysis(
                null, null, "데이터 없음", "데이터 없음", null, List.of(), List.of()
        );
    }
}
