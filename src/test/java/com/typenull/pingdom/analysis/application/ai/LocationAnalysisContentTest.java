package com.typenull.pingdom.analysis.application.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisContentTest {

    @Test
    void derivesBusinessSectionFromObservedFootTrafficWhenAiSectionIsEmpty() {
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

    @Test
    void derivesDemandAndUndividedTrafficMetricsFromAvailableTotals() {
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
