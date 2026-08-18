package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** AI JSON을 고정된 HTML 디자인으로 변환한다. */
@Component
public class LocationAnalysisHtmlComposer {

    public String compose(
            String reportId,
            String reportName,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            LocationAnalysisContent content
    ) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    @page { size: A4; margin: 18mm 15mm; }
                    body { font-family: sans-serif; color: #1f2937; line-height: 1.55; }
                    h1 { color: #0f766e; margin-bottom: 4px; }
                    h2 { color: #115e59; border-bottom: 1px solid #99f6e4; padding-bottom: 5px; margin-top: 26px; }
                    h3 { color: #374151; margin-bottom: 4px; }
                    .meta, .muted { color: #6b7280; font-size: 11px; }
                    .grade { display: inline-block; background: #ccfbf1; color: #115e59; padding: 4px 10px; border-radius: 12px; font-weight: bold; }
                    .card { border: 1px solid #d1d5db; border-radius: 6px; padding: 10px; margin: 8px 0; }
                    table { width: 100%%; border-collapse: collapse; margin: 8px 0 14px; font-size: 11px; }
                    th, td { border: 1px solid #d1d5db; padding: 6px; text-align: left; vertical-align: top; }
                    th { background: #f0fdfa; }
                    ul { margin-top: 4px; }
                  </style>
                </head>
                <body>
                  <h1>%s</h1>
                  <p class="meta">보고서 ID: %s<br>발행일자: %s<br>분석 기준일: %s</p>
                  %s
                </body>
                </html>
                """.formatted(
                escape(reportName),
                escape(reportId),
                publishedDate,
                analysisBasisDate,
                renderContent(content)
        );
    }

    private String renderContent(LocationAnalysisContent content) {
        LocationAnalysisContent.OverallLocationEvaluation overall = content.overallLocationEvaluation();
        LocationAnalysisContent.TargetPopulationAnalysis target = content.targetPopulationAnalysis();
        LocationAnalysisContent.FootTrafficAnalysis traffic = content.footTrafficAnalysis();
        LocationAnalysisContent.NearbyFacilities facilities = content.nearbyFacilities();
        return """
                <section><h2>종합 입지 평가</h2>
                  <span class="grade">%s</span><p>%s</p>
                  %s%s%s
                </section>
                <section><h2>타깃 인구 분석</h2><p>%s</p>%s%s%s</section>
                <section><h2>유동 인구 분석</h2><p>%s</p>%s%s%s%s</section>
                <section><h2>주변 시설</h2>%s%s%s%s</section>
                <section><h2>분석 범위 및 출처</h2>%s%s%s</section>
                """.formatted(
                escape(overall.grade().name()), escape(text(overall.summary())),
                renderStringList("강점", overall.strengths()),
                renderStringList("주의 요인", overall.risks()),
                renderEvidenceTable(overall.evidences()),
                escape(text(target.summary())), renderMetricTable("연령", target.age()),
                renderMetricTable("성별", target.gender()), renderEvidenceTable(target.evidences()),
                escape(text(traffic.summary())), renderValue("전체 유동 인구", traffic.total()),
                renderMetricTable("시간대", traffic.byTime()), renderMetricTable("요일", traffic.byDay()),
                renderEvidenceTable(traffic.evidences()),
                renderFacilityTable("경쟁 시설", facilities.competitors()),
                renderFacilityTable("편의 시설", facilities.convenienceFacilities()),
                renderFacilityTable("교통 시설", facilities.transportFacilities()),
                renderEvidenceTable(facilities.evidences()),
                renderScope(content.analysisScope()), renderDataSources(content.dataSources()),
                renderStringList("제한사항", content.limitations())
        );
    }

    private String renderStringList(String title, List<String> values) {
        if (values.isEmpty()) return "";
        return "<div class=\"card\"><h3>" + escape(title) + "</h3><ul>"
                + values.stream().map(value -> "<li>" + escape(text(value)) + "</li>").collect(Collectors.joining())
                + "</ul></div>";
    }

    private String renderMetricTable(String title, List<LocationAnalysisContent.Metric> metrics) {
        if (metrics.isEmpty()) return "";
        String rows = metrics.stream()
                .map(metric -> "<tr><td>" + escape(text(metric.label())) + "</td><td>"
                        + escape(value(metric.value())) + "</td><td>" + escape(text(metric.unit()))
                        + "</td><td>" + escape(value(metric.sharePercent())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>" + escape(title) + "</h3><table><tr><th>구분</th><th>값</th><th>단위</th><th>비율(%)</th></tr>"
                + rows + "</table>";
    }

    private String renderFacilityTable(String title, List<LocationAnalysisContent.Facility> facilities) {
        if (facilities.isEmpty()) return "";
        String rows = facilities.stream()
                .map(facility -> "<tr><td>" + escape(text(facility.name())) + "</td><td>"
                        + escape(text(facility.category())) + "</td><td>"
                        + escape(value(facility.distanceMeters())) + "m</td><td>"
                        + escape(text(facility.address())) + "</td><td>"
                        + escape(text(facility.description())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>" + escape(title) + "</h3><table><tr><th>시설명</th><th>분류</th><th>거리</th><th>주소</th><th>설명</th></tr>"
                + rows + "</table>";
    }

    private String renderEvidenceTable(List<LocationAnalysisContent.Evidence> evidences) {
        if (evidences.isEmpty()) return "";
        String rows = evidences.stream()
                .map(evidence -> "<tr><td>" + escape(text(evidence.type() == null ? null : evidence.type().name()))
                        + "</td><td>" + escape(text(evidence.source())) + "</td><td>"
                        + escape(text(evidence.reference())) + "</td><td>"
                        + escape(text(evidence.description())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>근거 데이터</h3><table><tr><th>유형</th><th>출처</th><th>참조</th><th>설명</th></tr>"
                + rows + "</table>";
    }

    private String renderScope(LocationAnalysisContent.AnalysisScope scope) {
        return "<div class=\"card\"><strong>요청 지역:</strong> " + escape(text(scope.requestedRegion()))
                + "<br><strong>정규화 지역:</strong> " + escape(text(scope.normalizedRegion()))
                + "<br><strong>범위:</strong> " + escape(text(scope.scopeLevel() == null ? null : scope.scopeLevel().name()))
                + "<br><strong>설명:</strong> " + escape(text(scope.scopeDescription()))
                + "<br><strong>반경:</strong> " + escape(value(scope.radiusMeters())) + "m</div>";
    }

    private String renderDataSources(List<LocationAnalysisContent.DataSource> sources) {
        if (sources.isEmpty()) return "";
        String rows = sources.stream()
                .map(source -> "<tr><td>" + escape(text(source.id())) + "</td><td>"
                        + escape(text(source.type() == null ? null : source.type().name())) + "</td><td>"
                        + escape(text(source.source())) + "</td><td>" + escape(text(source.reference())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>데이터 출처</h3><table><tr><th>ID</th><th>유형</th><th>출처</th><th>참조</th></tr>"
                + rows + "</table>";
    }

    private String renderValue(String label, Double value) {
        return "<p class=\"muted\"><strong>" + escape(label) + ":</strong> " + escape(value(value)) + "</p>";
    }

    private String value(Double value) {
        return value == null ? "데이터 없음" : String.valueOf(value);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "데이터 없음" : value;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "데이터 없음" : value);
    }
}
