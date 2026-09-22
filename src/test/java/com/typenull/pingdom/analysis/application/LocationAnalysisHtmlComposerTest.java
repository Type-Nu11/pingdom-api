package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisHtmlComposerTest {

    private final LocationAnalysisHtmlComposer composer = new LocationAnalysisHtmlComposer();

    /**
     * 고정 보고서 HTML에 서버 메타데이터·7개 섹션·페이지 표기·폰트·75점 표시와 데이터 부재 문구가 포함되는지 검증.
     * 기존 영문 섹션·원시 등급·Markdown·script 문자열의 혼입 방지 확인. 실제 렌더링은 검증 범위에서 제외.
     */
    @Test
    void rendersReportDesignAndMetadata() {
        String html = composer.compose(
                "report-1",
                "강남 카페 입지 분석",
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 17),
                new LocationAnalysisContent(
                        "강남 카페 입지 분석",
                        new LocationAnalysisContent.OverallLocationEvaluation(
                                LocationAnalysisContent.Grade.CONDITIONAL, 75d, "조건부 적합",
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
        assertThat(html).contains("page-break-after: always", "NanumGothic", "01 / 07", "02 / 07", "07 / 07");
        assertThat(html).contains("상권 개요와 후보 입지", "타깃 고객 분석", "유동 인구와 영업시간",
                "경쟁과 주변 환경", "주변 시설과 경쟁업체", "사업성 및 실행 전략", "데이터 신뢰도와 분석 기준");
        assertThat(html).doesNotContain("06 / LOCAL FACILITIES", "08 / DATA QUALITY & SOURCES");
        assertThat(html).contains("전체 평가도", "75.0\u00a0점");
        assertThat(html).doesNotContain(">CONDITIONAL<");
        assertThat(html).doesNotContain("####", "```", "<script");
        assertThat(html).contains("데이터 없음");
        assertThat(html).contains("경쟁업체 없음", "주변 시설 및 경쟁업체 없음");
        assertThat(html).doesNotContain("주변 시설 데이터 없음");
    }

    /**
     * 비중이 없는 평균 활동 시간 지표도 행동 지표 카드에 값 18.5 시로 출력되는지 검증.
     */
    @Test
    void rendersBehaviorMetricWithoutShare() {
        String html = composer.compose(
                "report-1", "강남 카페 입지 분석", LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 17),
                new LocationAnalysisContent(
                        "강남 카페 입지 분석", null, null,
                        new LocationAnalysisContent.TargetPopulationAnalysis(
                                "타깃 분석", "강남역",
                                List.of(), List.of(),
                                List.of(new LocationAnalysisContent.Metric("평균 활동 시간", 18.5d, "시", null)),
                                List.of()
                        ),
                        null, null, null, null, null, List.of(), null, List.of(), List.of()
                )
        );

        assertThat(html).contains("행동 지표", "18.5 시");
    }
}
