package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** 구조화된 AI 응답을 고정된 3페이지 이상 XHTML 디자인으로 변환한다. */
@Component
public class LocationAnalysisHtmlComposer {

    public String compose(
            String reportId,
            String reportName,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            LocationAnalysisContent content
    ) {
        LocationAnalysisContent.OverallLocationEvaluation overall = content.overallLocationEvaluation();
        LocationAnalysisContent.TargetPopulationAnalysis target = content.targetPopulationAnalysis();
        LocationAnalysisContent.FootTrafficAnalysis traffic = content.footTrafficAnalysis();
        LocationAnalysisContent.NearbyFacilities facilities = content.nearbyFacilities();

        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8" />
                  <style>
                    @page { size: A4; margin: 0; }
                    * { box-sizing: border-box; }
                    body { margin: 0; background: #e8e7e1; color: #292b2a; font-family: 'NanumGothic', 'Noto Sans KR', sans-serif; font-size: 10px; line-height: 1.55; }
                    .page { position: relative; width: 210mm; min-height: 297mm; padding: 22mm 19mm 20mm; background: #f8f7f2; page-break-after: always; }
                    .page:last-child { page-break-after: auto; }
                    .eyebrow { color: #7d8777; font-size: 9px; letter-spacing: 1.6px; text-transform: uppercase; }
                    .section-number { float: left; margin: -2px 18px 0 0; color: #202321; font-family: Georgia, serif; font-size: 38px; line-height: .9; }
                    h1 { max-width: 145mm; margin: 12mm 0 4mm; color: #222522; font-family: Georgia, 'NanumGothic', sans-serif; font-size: 29px; font-weight: 400; line-height: 1.2; }
                    h2 { margin: 0 0 7mm; padding-top: 2mm; border-top: 1px solid #aeb4a8; color: #222522; font-family: Georgia, 'NanumGothic', sans-serif; font-size: 21px; font-weight: 400; line-height: 1.2; }
                    h3 { margin: 5mm 0 2mm; color: #424842; font-size: 11px; font-weight: 700; }
                    p { margin: 2mm 0; }
                    .rule { height: 1px; margin: 7mm 0; background: #d5d7cf; }
                    .meta { color: #737970; font-size: 9px; line-height: 1.8; }
                    .lead { max-width: 150mm; color: #555b54; font-size: 12px; line-height: 1.8; }
                    .grade { display: inline-block; margin: 2mm 0 3mm; padding: 2mm 5mm; color: #f8f7f2; background: #7d8777; font-size: 11px; letter-spacing: .7px; }
                    .card-grid { display: table; width: 100%%; table-layout: fixed; border-spacing: 3mm 0; margin: 5mm -3mm 7mm; }
                    .card-grid > .card { display: table-cell; width: 33.333%%; padding: 5mm; border-top: 3px solid #aeb4a8; background: #eeeee7; vertical-align: top; }
                    .card-label { color: #7b8179; font-size: 9px; }
                    .card-value { margin-top: 2mm; color: #282c29; font-family: Georgia, 'NanumGothic', sans-serif; font-size: 20px; }
                    .panel { margin: 4mm 0; padding: 5mm; background: #eeeee7; page-break-inside: avoid; }
                    .panel.dark { color: #f8f7f2; background: #303531; }
                    .panel.dark h3, .panel.dark .muted { color: #d4d9ce; }
                    .muted { color: #737970; font-size: 9px; }
                    .list { margin: 2mm 0 0; padding-left: 5mm; }
                    .list li { margin: 1mm 0; }
                    table { width: 100%%; border-collapse: collapse; margin: 3mm 0 6mm; font-size: 9px; page-break-inside: avoid; }
                    th, td { padding: 2.5mm 2mm; border-bottom: 1px solid #d5d7cf; text-align: left; vertical-align: top; }
                    th { color: #697166; background: #e7e8e0; font-weight: 700; }
                    .bar-row { display: table; width: 100%%; margin: 2mm 0; table-layout: fixed; }
                    .bar-label, .bar-value { display: table-cell; width: 28%%; vertical-align: middle; }
                    .bar-track { display: table-cell; width: 54%%; height: 4mm; background: #dfe2d9; vertical-align: middle; }
                    .bar-fill { display: block; height: 4mm; background: #7d8777; }
                    .bar-value { width: 18%%; padding-left: 2mm; color: #697166; text-align: right; }
                    .footer { position: absolute; right: 19mm; bottom: 9mm; color: #858b82; font-family: Georgia, serif; font-size: 9px; }
                    .avoid-break { page-break-inside: avoid; }
                  </style>
                </head>
                <body>
                  <section class="page">
                    <div class="eyebrow">PINGDOM / LOCATION INTELLIGENCE</div>
                    <div class="section-number">01</div>
                    <h1>%s</h1>
                    <p class="lead">데이터로 확인한 지역의 기회와 위험을 한눈에 확인하는 상권·입지 분석 보고서입니다.</p>
                    <div class="rule"></div>
                    <p class="meta">보고서 ID: %s<br />발행일자: %s<br />분석 기준일: %s</p>
                    <div class="card-grid">
                      <div class="card"><div class="card-label">종합 등급</div><div class="card-value">%s</div></div>
                      <div class="card"><div class="card-label">분석 범위</div><div class="card-value">%s</div></div>
                      <div class="card"><div class="card-label">추천 장소</div><div class="card-value">%s</div></div>
                    </div>
                    <div class="panel dark">
                      <h3>종합 입지 평가</h3>
                      <span class="grade">%s</span>
                      <p>%s</p>
                    </div>
                    %s
                    %s
                    <div class="footer">01 / 03</div>
                  </section>

                  <section class="page">
                    <div class="eyebrow">02 / PEOPLE &amp; FLOW</div>
                    <h2><span class="section-number">02</span>타깃 인구와 유동 인구</h2>
                    <div class="panel avoid-break"><h3>추천 장소</h3>%s</div>
                    <div class="card-grid">
                      <div class="card"><div class="card-label">전체 유동 인구</div><div class="card-value">%s</div></div>
                      <div class="card"><div class="card-label">주요 연령</div><div class="card-value">%s</div></div>
                      <div class="card"><div class="card-label">주요 성별</div><div class="card-value">%s</div></div>
                    </div>
                    <div class="panel avoid-break"><h3>타깃 인구 분석</h3><p>%s</p><p class="muted">산출 기준 장소: %s</p>%s%s</div>
                    <div class="panel avoid-break"><h3>유동 인구 분석</h3><p>%s</p>%s%s%s</div>
                    <div class="footer">02 / 03</div>
                  </section>

                  <section class="page">
                    <div class="eyebrow">03 / CONTEXT &amp; EVIDENCE</div>
                    <h2><span class="section-number">03</span>주변 환경과 검증 근거</h2>
                    %s
                    <div class="panel avoid-break"><h3>분석 범위</h3>%s</div>
                    %s
                    %s
                    <div class="footer">03 / 03</div>
                  </section>
                </body>
                </html>
                """.formatted(
                escape(text(reportName)),
                escape(text(reportId)),
                escape(String.valueOf(publishedDate)),
                escape(String.valueOf(analysisBasisDate)),
                escape(overall == null || overall.grade() == null ? null : overall.grade().name()),
                escape(content.analysisScope() == null ? null : content.analysisScope().normalizedRegion()),
                escape(value(content.recommendedPlaces().size())),
                escape(overall == null || overall.grade() == null ? null : overall.grade().name()),
                escape(overall == null ? null : overall.summary()),
                renderStringList("강점", overall == null ? List.of() : overall.strengths()),
                renderStringList("주의 요인", overall == null ? List.of() : overall.risks()),
                renderRecommendedPlaces(content.recommendedPlaces()),
                escape(value(traffic == null ? null : traffic.total())),
                escape(topMetric(target == null ? List.of() : target.age())),
                escape(topMetric(target == null ? List.of() : target.gender())),
                escape(target == null ? null : target.summary()),
                escape(target == null ? null : target.derivedFromPlace()),
                renderMetricBars("연령", target == null ? List.of() : target.age()),
                renderMetricBars("성별", target == null ? List.of() : target.gender()),
                escape(traffic == null ? null : traffic.summary()),
                renderMetricBars("시간대", traffic == null ? List.of() : traffic.byTime()),
                renderMetricBars("요일", traffic == null ? List.of() : traffic.byDay()),
                renderEvidenceTable(traffic == null ? List.of() : traffic.evidences()),
                renderFacilities(facilities),
                renderScope(content.analysisScope()),
                renderDataSources(content.dataSources()),
                renderStringList("제한사항", content.limitations())
        );
    }

    private String renderRecommendedPlaces(List<LocationAnalysisContent.RecommendedPlace> places) {
        if (places.isEmpty()) return "<p class=\"muted\">데이터 없음</p>";
        String rows = places.stream().map(place -> "<tr><td>" + escape(value(place.rank())) + "</td><td>"
                + escape(text(place.name())) + "</td><td>" + escape(text(place.address())) + "</td><td>"
                + escape(value(place.score())) + "</td><td>" + escape(text(place.reason())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th>순위</th><th>장소</th><th>주소</th><th>점수</th><th>추천 근거</th></tr>" + rows + "</table>";
    }

    private String renderFacilities(LocationAnalysisContent.NearbyFacilities facilities) {
        if (facilities == null) return "<h3>주변 시설</h3><p class=\"muted\">주변 시설 데이터 없음</p>";
        String rendered = renderFacilityTable("경쟁 시설", facilities.competitors())
                + renderFacilityTable("편의 시설", facilities.convenienceFacilities())
                + renderFacilityTable("교통 시설", facilities.transportFacilities())
                + renderEvidenceTable(facilities.evidences());
        return "<h3>주변 시설</h3>" + (rendered.isBlank() ? "<p class=\"muted\">주변 시설 데이터 없음</p>" : rendered);
    }

    private String renderMetricBars(String title, List<LocationAnalysisContent.Metric> metrics) {
        if (metrics.isEmpty()) return "<p class=\"muted\">" + escape(title) + ": 데이터 없음</p>";
        return "<h3>" + escape(title) + " 분포</h3>" + metrics.stream().map(metric -> {
            double share = metric.sharePercent() == null ? 0d : Math.max(0d, Math.min(100d, metric.sharePercent()));
            return "<div class=\"bar-row\"><span class=\"bar-label\">" + escape(text(metric.label()))
                    + "</span><span class=\"bar-track\"><span class=\"bar-fill\" style=\"width:"
                    + String.format(java.util.Locale.ROOT, "%.2f", share) + "%\"></span></span><span class=\"bar-value\">"
                    + escape(value(metric.sharePercent())) + "%</span></div>";
        }).collect(Collectors.joining());
    }

    private String renderStringList(String title, List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return "<div class=\"panel avoid-break\"><h3>" + escape(title) + "</h3><ul class=\"list\">"
                + values.stream().filter(Objects::nonNull).map(value -> "<li>" + escape(text(value)) + "</li>")
                .collect(Collectors.joining()) + "</ul></div>";
    }

    private String renderFacilityTable(String title, List<LocationAnalysisContent.Facility> facilities) {
        if (facilities == null || facilities.isEmpty()) return "";
        String rows = facilities.stream().map(facility -> "<tr><td>" + escape(text(facility.name())) + "</td><td>"
                + escape(text(facility.category())) + "</td><td>" + escape(value(facility.distanceMeters())) + "m</td><td>"
                + escape(text(facility.address())) + "</td><td>" + escape(text(facility.description())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>" + escape(title) + "</h3><table><tr><th>시설명</th><th>분류</th><th>거리</th><th>주소</th><th>설명</th></tr>" + rows + "</table>";
    }

    private String renderEvidenceTable(List<LocationAnalysisContent.Evidence> evidences) {
        if (evidences == null || evidences.isEmpty()) return "";
        String rows = evidences.stream().map(evidence -> "<tr><td>" + escape(text(evidence.id())) + "</td><td>"
                + escape(text(evidence.type() == null ? null : evidence.type().name())) + "</td><td>"
                + escape(text(evidence.source())) + "</td><td>" + escape(text(evidence.description())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>근거 데이터</h3><table><tr><th>ID</th><th>유형</th><th>출처</th><th>설명</th></tr>" + rows + "</table>";
    }

    private String renderScope(LocationAnalysisContent.AnalysisScope scope) {
        if (scope == null) return "<p class=\"muted\">분석 범위 데이터 없음</p>";
        return "<p><strong>요청 지역:</strong> " + escape(text(scope.requestedRegion())) + "<br />"
                + "<strong>정규화 지역:</strong> " + escape(text(scope.normalizedRegion())) + "<br />"
                + "<strong>적용 범위:</strong> " + escape(text(scope.scopeDescription())) + "<br />"
                + "<strong>반경:</strong> " + escape(radius(scope.radiusMeters())) + "</p>";
    }

    private String renderDataSources(List<LocationAnalysisContent.DataSource> sources) {
        if (sources == null || sources.isEmpty()) return "<p class=\"muted\">데이터 출처 없음</p>";
        String rows = sources.stream().map(source -> "<tr><td>" + escape(text(source.id())) + "</td><td>"
                + escape(text(source.type() == null ? null : source.type().name())) + "</td><td>"
                + escape(text(source.source())) + "</td><td>" + escape(text(source.reference())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>데이터 출처</h3><table><tr><th>ID</th><th>유형</th><th>출처</th><th>참조</th></tr>" + rows + "</table>";
    }

    private String topMetric(List<LocationAnalysisContent.Metric> metrics) {
        return metrics.stream().filter(metric -> metric != null && metric.sharePercent() != null)
                .max(java.util.Comparator.comparing(LocationAnalysisContent.Metric::sharePercent))
                .map(metric -> text(metric.label())).orElse("데이터 없음");
    }

    private String value(Double value) { return value == null ? "데이터 없음" : String.format(java.util.Locale.ROOT, "%,.1f", value); }

    private String radius(Double value) { return value == null ? "데이터 없음" : value(value) + "m"; }

    private String value(Integer value) { return value == null ? "데이터 없음" : String.valueOf(value); }

    private String text(String value) { return value == null || value.isBlank() ? "데이터 없음" : value; }

    private String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "데이터 없음" : value); }
}
