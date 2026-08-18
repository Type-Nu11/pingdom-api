package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LocationAnalysisHtmlComposerTest {

    private final LocationAnalysisHtmlComposer composer = new LocationAnalysisHtmlComposer();

    @Test
    void addsServerReportMetadataAroundAiHtml() {
        String html = composer.compose(
                "report-1",
                "강남 카페 입지 분석",
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 17),
                "<h2>종합 입지 평가</h2>"
        );

        assertThat(html).contains("report-1", "강남 카페 입지 분석", "2026-08-17", "종합 입지 평가");
        assertThat(html).contains("<!doctype html>", "<meta charset=\"UTF-8\">");
    }
}
