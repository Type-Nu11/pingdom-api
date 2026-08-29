package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisHtmlComposerTest {

    private final LocationAnalysisHtmlComposer composer = new LocationAnalysisHtmlComposer();

    @Test
    void rendersFixedReportDesignAndServerMetadata() {
        String html = composer.compose(
                "report-1",
                "강남 카페 입지 분석",
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 17),
                new LocationAnalysisContent(
                        "강남 카페 입지 분석",
                        new LocationAnalysisContent.OverallLocationEvaluation(
                                LocationAnalysisContent.Grade.CONDITIONAL, "조건부 적합",
                                List.of("유동인구 확인"), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.TargetPopulationAnalysis(
                                "20대 중심", List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.FootTrafficAnalysis(
                                "시간대별 분석", 100d, List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.NearbyFacilities(
                                List.of(), List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.AnalysisScope(
                                "서울 강남구", "서울특별시 강남구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                                "구 전체", null
                        ),
                        List.of(), List.of()
                )
        );

        assertThat(html).contains("report-1", "강남 카페 입지 분석", "2026-08-17", "종합 입지 평가");
        assertThat(html).contains("<!DOCTYPE html>", "<meta charset=\"UTF-8\" />");
        assertThat(html).contains("타깃 고객 분석", "유동 인구와 영업시간", "주변 시설");
        assertThat(html).contains("page-break-after: always", "NanumGothic", "01 / 08", "02 / 08", "08 / 08");
        assertThat(html).contains("상권 개요와 후보 입지", "타깃 고객 분석", "유동 인구와 영업시간",
                "경쟁과 주변 환경", "주변 시설과 접근성", "사업성 및 실행 전략", "데이터 신뢰도와 분석 기준");
        assertThat(html).doesNotContain("####", "```", "<script");
        assertThat(html).contains("데이터 없음");
        assertThat(html).contains("경쟁업체 없음", "주변 시설 없음");
        assertThat(html).doesNotContain("주변 시설 데이터 없음");
    }
}
