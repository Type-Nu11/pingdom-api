package com.typenull.pingdom.analysis.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisContentTest {

    /**
     * AI 사업성 내용이 비어 있으면 관측 유동 12,240명과 영업시간 적합도·피크 시간·평균 활동 시간을 사업성 지표로 파생하는지 검증.
     */
    @Test
    void derivesMissingBusinessPerformance() {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석", null, null,
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "타깃", "추천 장소", List.of(), List.of(),
                        List.of(new LocationAnalysisContent.Metric("평균 활동 시간", 18.5d, "시", null)), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "유동", 12_240d,
                        List.of(new LocationAnalysisContent.Metric("18-20시", 3_200d, "명", 26.1d)),
                        List.of(), List.of(), "영업시간과 유동이 겹침", 82d, List.of()
                ),
                null, null,
                new LocationAnalysisContent.BusinessPerformanceAnalysis("데이터 없음", List.of(), List.of(), List.of(), List.of()),
                null, List.of(), null, List.of(), List.of()
        );

        LocationAnalysisContent enriched = content.withDerivedBusinessPerformance();

        assertThat(enriched.businessPerformanceAnalysis().summary()).contains("12,240명");
        assertThat(enriched.businessPerformanceAnalysis().performanceIndicators())
                .extracting(LocationAnalysisContent.Metric::label)
                .contains("관측 유동 인구", "영업시간 적합도", "피크 유동 시간대(18-20시)", "평균 활동 시간");
    }

    /**
     * 전체 유동·타깃 연령 값으로 상권 수요 지표를 보완하고 시간·요일·월 분포는 전체 관측값으로 명시하는지 검증.
     * 집계만으로 세부 분포가 존재하는 것처럼 표현되는 회귀를 방지.
     */
    @Test
    void derivesAvailableTrafficTotals() {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석", null,
                new LocationAnalysisContent.CommercialAreaAnalysis(
                        "잠실", "상업지역", "데이터 없음", List.of(), List.of()
                ),
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "타깃", "와플대학", List.of(
                        new LocationAnalysisContent.Metric("20~39세", 1_169d, "명", 50d)
                ), List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "데이터 없음", 2_328d, List.of(), List.of(), List.of(),
                        "데이터 없음", null, List.of()
                ),
                null, null, null, null, List.of(), null, List.of(), List.of()
        );

        LocationAnalysisContent enriched = content.withDerivedReportMetrics();

        assertThat(enriched.commercialAreaAnalysis().summary()).contains("관측 유동 인구");
        assertThat(enriched.commercialAreaAnalysis().demandIndicators())
                .extracting(LocationAnalysisContent.Metric::label)
                .contains("관측 유동 인구", "타깃 연령 일치 관측");
        assertThat(enriched.footTrafficAnalysis().byTime())
                .extracting(LocationAnalysisContent.Metric::label)
                .containsExactly("전체 관측값(시간대 구분 없음)");
        assertThat(enriched.footTrafficAnalysis().byDay())
                .extracting(LocationAnalysisContent.Metric::label)
                .containsExactly("전체 관측값(요일 구분 없음)");
        assertThat(enriched.footTrafficAnalysis().byMonth())
                .extracting(LocationAnalysisContent.Metric::label)
                .containsExactly("전체 관측값(월 구분 없음)");
    }
}
