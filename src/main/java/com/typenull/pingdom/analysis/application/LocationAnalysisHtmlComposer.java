package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 구조화된 AI 응답을 고정된 7페이지 XHTML 디자인으로 변환한다. */
@Component
public class LocationAnalysisHtmlComposer {

    private static final int TOTAL_PAGES = 7;
    private static final int MAX_TABLE_ROWS = 5;
    private static final int MAX_FACILITY_ROWS = 2;

    public String compose(
            String reportId,
            String reportName,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            LocationAnalysisContent content
    ) {
        LocationAnalysisContent.OverallLocationEvaluation overall = content.overallLocationEvaluation();
        LocationAnalysisContent.CommercialAreaAnalysis commercialArea = content.commercialAreaAnalysis();
        LocationAnalysisContent.TargetPopulationAnalysis target = content.targetPopulationAnalysis();
        LocationAnalysisContent.FootTrafficAnalysis traffic = content.footTrafficAnalysis();
        LocationAnalysisContent.NearbyFacilities facilities = content.nearbyFacilities();
        LocationAnalysisContent.CompetitionAnalysis competition = content.competitionAnalysis();
        LocationAnalysisContent.BusinessPerformanceAnalysis performance = content.businessPerformanceAnalysis();
        LocationAnalysisContent.DataQualityAnalysis dataQuality = content.dataQualityAnalysis();

        return documentStart()
                + coverPage(reportId, reportName, publishedDate, analysisBasisDate, content, overall, commercialArea)
                + page(2, "02 / MARKET & CANDIDATES", "상권 개요와 후보 입지",
                panel("상권 개요", text(commercialArea == null ? null : commercialArea.summary()))
                        + renderCards(
                        "상권명", commercialArea == null ? null : commercialArea.name(),
                        "상권 유형", commercialArea == null ? null : commercialArea.type(),
                        "적용 반경", radius(content.analysisScope() == null ? null : content.analysisScope().radiusMeters())
                )
                        + panel("수요 유발 지표", renderMetricTable(commercialArea == null
                        ? List.of() : commercialArea.demandIndicators()))
                        + panel("추천 후보 비교", renderRecommendedPlaces(content.recommendedPlaces()))
                        + renderEvidenceTable(commercialArea == null ? List.of() : commercialArea.evidences()))
                + page(3, "03 / TARGET CUSTOMER", "타깃 고객 분석",
                panel("타깃 고객 요약", text(target == null ? null : target.summary())
                        + muted("산출 기준 장소: " + text(target == null ? null : target.derivedFromPlace())))
                        + renderCards(
                        "주요 연령", topMetric(target == null ? List.of() : target.age()),
                        "주요 성별", topMetric(target == null ? List.of() : target.gender()),
                        "행동 지표", topMetric(target == null ? List.of() : target.behaviorIndicators())
                )
                        + panel("연령 분포", renderMetricBars(target == null ? List.of() : target.age()))
                        + panel("성별 분포", renderMetricBars(target == null ? List.of() : target.gender()))
                        + panel("체류·재방문·소비 행동", renderMetricTable(target == null
                        ? List.of() : target.behaviorIndicators()))
                        + renderEvidenceTable(target == null ? List.of() : target.evidences()))
                + page(4, "04 / FLOW & HOURS", "유동 인구와 영업시간",
                panel("유동 인구 요약", text(traffic == null ? null : traffic.summary()))
                        + renderCards(
                        "전체 유동 인구", value(traffic == null ? null : traffic.total()),
                        "영업시간 적합도", score(traffic == null ? null : traffic.operatingHoursFitScore()),
                        "영업시간 판단", text(traffic == null ? null : traffic.operatingHoursAssessment())
                )
                        + panel("시간대별 유동", renderMetricBars(traffic == null ? List.of() : traffic.byTime()))
                        + panel("요일별 유동", renderMetricBars(traffic == null ? List.of() : traffic.byDay()))
                        + panel("월별 유동 추이", renderMetricBars(traffic == null ? List.of() : traffic.byMonth()))
                        + renderEvidenceTable(traffic == null ? List.of() : traffic.evidences()))
                + page(5, "05 / COMPETITION & CONTEXT", "경쟁과 주변 환경",
                panel("경쟁 환경", sectionText(competition == null ? null : competition.summary(), "경쟁업체 없음"))
                        + renderCards(
                        "전체 경쟁점", value(competition == null ? null : competition.totalCompetitors()),
                        "조회 반경", "100m",
                        "판정 기준", "동일 업종"
                )
                        + panel("주변 시설과 경쟁업체", renderNearbyContext(competition, facilities))
                        + renderEvidenceTable(competition == null ? List.of() : competition.evidences()))
                + page(6, "06 / BUSINESS POTENTIAL", "사업성 및 실행 전략",
                panel("사업성 요약", text(performance == null ? null : performance.summary()))
                        + panel("핵심 사업성 지표", renderMetricTable(performance == null
                        ? List.of() : performance.performanceIndicators()))
                        + panel("기회 요인", renderStringList("확인된 기회", performance == null
                        ? List.of() : performance.opportunities()))
                        + panel("실행 전 확인할 위험", renderStringList("위험 및 대응 필요 항목", performance == null
                        ? List.of() : performance.risks()))
                        + renderEvidenceTable(performance == null ? List.of() : performance.evidences()))
                + page(7, "07 / DATA QUALITY & SOURCES", "데이터 신뢰도와 분석 기준",
                renderCards(
                        "데이터 신뢰도", score(dataQuality == null ? null : dataQuality.reliabilityScore()),
                        "관측 수", value(dataQuality == null ? null : dataQuality.observationCount()),
                        "반경 확장", booleanText(dataQuality == null ? null : dataQuality.radiusExpanded())
                )
                        + panel("관측 범위", "<p><strong>관측 기간:</strong> "
                        + escape(text(dataQuality == null ? null : dataQuality.observationPeriod())) + "<br />"
                        + "<strong>데이터 범위:</strong> "
                        + escape(text(dataQuality == null ? null : dataQuality.coverage())) + "</p>")
                        + panel("분석 범위", renderScope(content.analysisScope()))
                        + panel("누락·제한 사항", renderStringList("확인하지 못한 데이터", dataQuality == null
                        ? List.of() : dataQuality.missingData()) + renderStringList("분석 제한사항", content.limitations()))
                        + renderDataSources(content.dataSources())
                        + renderEvidenceTable(dataQuality == null ? List.of() : dataQuality.evidences()))
                + documentEnd();
    }

    private String documentStart() {
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
                    h3 { margin: 0 0 2mm; color: #424842; font-size: 11px; font-weight: 700; }
                    p { margin: 2mm 0; }
                    .rule { height: 1px; margin: 7mm 0; background: #d5d7cf; }
                    .meta { color: #737970; font-size: 9px; line-height: 1.8; }
                    .lead { max-width: 150mm; color: #555b54; font-size: 12px; line-height: 1.8; }
                    .grade { display: inline-block; margin: 2mm 0 3mm; padding: 2mm 5mm; color: #f8f7f2; background: #7d8777; font-size: 11px; letter-spacing: .7px; }
                    .card-grid { display: table; width: 100%; table-layout: fixed; border-spacing: 3mm 0; margin: 5mm -3mm 7mm; }
                    .card-grid > .card { display: table-cell; width: 33.333%; padding: 5mm; border-top: 3px solid #aeb4a8; background: #eeeee7; vertical-align: top; }
                    .card-label { color: #7b8179; font-size: 9px; }
                    .card-value { margin-top: 2mm; color: #282c29; font-family: Georgia, 'NanumGothic', sans-serif; font-size: 16px; word-break: break-word; }
                    .panel { margin: 4mm 0; padding: 5mm; background: #eeeee7; page-break-inside: avoid; }
                    .panel.dark { color: #f8f7f2; background: #303531; }
                    .panel.dark h3, .panel.dark .muted { color: #d4d9ce; }
                    .muted { color: #737970; font-size: 9px; }
                    .list { margin: 2mm 0 0; padding-left: 5mm; }
                    .list li { margin: 1mm 0; }
                    table { width: 100%; border-collapse: collapse; margin: 3mm 0 6mm; font-size: 9px; page-break-inside: avoid; }
                    th, td { padding: 2.5mm 2mm; border-bottom: 1px solid #d5d7cf; text-align: left; vertical-align: top; }
                    th { color: #697166; background: #e7e8e0; font-weight: 700; }
                    .bar-row { display: table; width: 100%; margin: 2mm 0; table-layout: fixed; }
                    .bar-label, .bar-value { display: table-cell; width: 28%; vertical-align: middle; }
                    .bar-track { display: table-cell; width: 54%; height: 4mm; background: #dfe2d9; vertical-align: middle; }
                    .bar-fill { display: block; height: 4mm; background: #7d8777; }
                    .bar-value { width: 18%; padding-left: 2mm; color: #697166; text-align: right; }
                    .footer { position: absolute; right: 19mm; bottom: 9mm; color: #858b82; font-family: Georgia, serif; font-size: 9px; }
                  </style>
                </head>
                <body>
                """;
    }

    private String documentEnd() {
        return "</body></html>";
    }

    private String coverPage(
            String reportId,
            String reportName,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            LocationAnalysisContent content,
            LocationAnalysisContent.OverallLocationEvaluation overall,
            LocationAnalysisContent.CommercialAreaAnalysis commercialArea
    ) {
        return """
                <section class="page">
                  <div class="eyebrow">PINGDOM / LOCATION INTELLIGENCE</div>
                  <div class="section-number">01</div>
                  <h1>%s</h1>
                  <p class="lead">데이터로 확인한 지역의 기회와 위험을 한눈에 확인하는 상권·입지 분석 보고서입니다.</p>
                  <div class="rule"></div>
                  <p class="meta">보고서 ID: %s<br />발행일자: %s<br />분석 기준일: %s</p>
                  %s
                  <div class="panel dark"><h3>종합 입지 평가</h3><span class="grade">%s</span><p>%s</p></div>
                  %s%s
                  <div class="footer">01 / %02d</div>
                </section>
                """.formatted(
                escape(text(reportName)),
                escape(text(reportId)),
                escape(String.valueOf(publishedDate)),
                escape(String.valueOf(analysisBasisDate)),
                renderCards(
                        "전체 평가도", score(overall == null ? null : overall.overallScore()),
                        "분석 범위", content.analysisScope() == null ? null : content.analysisScope().normalizedRegion(),
                        "상권", commercialArea == null ? null : commercialArea.name()
                ),
                escape(score(overall == null ? null : overall.overallScore())),
                escape(text(overall == null ? null : overall.summary())),
                renderStringList("강점", overall == null ? List.of() : overall.strengths()),
                renderStringList("주의 요인", overall == null ? List.of() : overall.risks()),
                TOTAL_PAGES
        );
    }

    private String page(int number, String eyebrow, String title, String body) {
        return """
                <section class="page">
                  <div class="eyebrow">%s</div>
                  <h2><span class="section-number">%02d</span>%s</h2>
                  %s
                  <div class="footer">%02d / %02d</div>
                </section>
                """.formatted(escape(eyebrow), number, escape(title), body, number, TOTAL_PAGES);
    }

    private String panel(String title, String body) {
        return "<div class=\"panel\"><h3>" + escape(title) + "</h3>" + body + "</div>";
    }

    private String muted(String value) {
        return "<p class=\"muted\">" + escape(value) + "</p>";
    }

    private String renderCards(String... labelValuePairs) {
        StringBuilder cards = new StringBuilder("<div class=\"card-grid\">");
        for (int index = 0; index < labelValuePairs.length; index += 2) {
            cards.append("<div class=\"card\"><div class=\"card-label\">")
                    .append(escape(labelValuePairs[index]))
                    .append("</div><div class=\"card-value\">")
                    .append(escape(text(labelValuePairs[index + 1])))
                    .append("</div></div>");
        }
        return cards.append("</div>").toString();
    }

    private String renderRecommendedPlaces(List<LocationAnalysisContent.RecommendedPlace> places) {
        if (places.isEmpty()) return muted("데이터 없음");
        String rows = places.stream().limit(MAX_TABLE_ROWS).map(place -> "<tr><td>" + escape(value(place.rank())) + "</td><td>"
                + escape(text(place.name())) + "</td><td>" + escape(text(place.address())) + "</td><td>"
                + escape(score(place.score())) + "</td><td>" + escape(text(place.reason())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th>순위</th><th>장소</th><th>주소</th><th>점수</th><th>추천 근거</th></tr>" + rows + "</table>"
                + truncationNotice(places.size(), MAX_TABLE_ROWS, "추천 후보");
    }

    private String renderMetricBars(List<LocationAnalysisContent.Metric> metrics) {
        if (metrics.isEmpty()) return muted("데이터 없음");
        return metrics.stream().limit(MAX_TABLE_ROWS).map(metric -> {
            double share = metric.sharePercent() == null ? 0d : Math.max(0d, Math.min(100d, metric.sharePercent()));
            String display = metric.sharePercent() == null
                    ? value(metric.value()) + " " + text(metric.unit())
                    : value(metric.sharePercent()) + "%";
            return "<div class=\"bar-row\"><span class=\"bar-label\">" + escape(text(metric.label()))
                    + "</span><span class=\"bar-track\"><span class=\"bar-fill\" style=\"width:"
                    + String.format(java.util.Locale.ROOT, "%.2f", share) + "%\"></span></span><span class=\"bar-value\">"
                    + escape(display) + "</span></div>";
        }).collect(Collectors.joining());
    }

    private String renderMetricTable(List<LocationAnalysisContent.Metric> metrics) {
        if (metrics.isEmpty()) return muted("데이터 없음");
        String rows = metrics.stream().limit(MAX_TABLE_ROWS).map(metric -> "<tr><td>" + escape(text(metric.label())) + "</td><td>"
                + escape(value(metric.value())) + " " + escape(text(metric.unit())) + "</td><td>"
                + escape(metric.sharePercent() == null ? "-" : value(metric.sharePercent()) + "%") + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th>지표</th><th>값</th><th>비율</th></tr>" + rows + "</table>"
                + truncationNotice(metrics.size(), MAX_TABLE_ROWS, "지표");
    }

    private String renderStringList(String title, List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return "<div><h3>" + escape(title) + "</h3><ul class=\"list\">"
                + values.stream().filter(Objects::nonNull).limit(MAX_TABLE_ROWS).map(value -> "<li>" + escape(text(value)) + "</li>")
                .collect(Collectors.joining()) + "</ul></div>";
    }

    private String renderNearbyContext(
            LocationAnalysisContent.CompetitionAnalysis competition,
            LocationAnalysisContent.NearbyFacilities facilities
    ) {
        java.util.ArrayList<ContextFacility> entries = new java.util.ArrayList<>();
        if (competition != null) {
            competition.keyCompetitors().forEach(facility -> entries.add(new ContextFacility("경쟁 업체", facility)));
        }
        if (facilities != null) {
            facilities.convenienceFacilities().forEach(facility -> entries.add(new ContextFacility("주변 시설", facility)));
            facilities.transportFacilities().forEach(facility -> entries.add(new ContextFacility("교통 시설", facility)));
        }
        if (entries.isEmpty()) {
            return muted("주변 시설 및 경쟁업체 없음");
        }
        String rows = entries.stream().limit(MAX_FACILITY_ROWS * 3).map(entry -> {
            LocationAnalysisContent.Facility facility = entry.facility();
            return "<tr><td>" + escape(entry.type()) + "</td><td>" + escape(text(facility.name())) + "</td><td>"
                    + escape(text(facility.category())) + "</td><td>" + escape(value(facility.distanceMeters())) + "m</td><td>"
                    + escape(text(facility.address())) + "</td></tr>";
        }).collect(Collectors.joining());
        return "<table><tr><th>구분</th><th>시설명</th><th>분류</th><th>거리</th><th>주소</th></tr>" + rows + "</table>"
                + truncationNotice(entries.size(), MAX_FACILITY_ROWS * 3, "주변 시설과 경쟁업체");
    }

    private String sectionText(String value, String fallback) {
        return value == null || value.isBlank() || "데이터 없음".equals(value)
                ? muted(fallback)
                : text(value);
    }

    private String renderEvidenceTable(List<LocationAnalysisContent.Evidence> evidences) {
        if (evidences == null || evidences.isEmpty()) return "";
        String rows = evidences.stream().limit(MAX_TABLE_ROWS).map(evidence -> "<tr><td>" + escape(text(evidence.id())) + "</td><td>"
                + escape(text(evidence.type() == null ? null : evidence.type().name())) + "</td><td>"
                + escape(text(evidence.source())) + "</td><td>" + escape(text(evidence.description())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>근거 데이터</h3><table><tr><th>ID</th><th>유형</th><th>출처</th><th>설명</th></tr>" + rows + "</table>";
    }

    private String renderScope(LocationAnalysisContent.AnalysisScope scope) {
        if (scope == null) return muted("분석 범위 데이터 없음");
        return "<p><strong>요청 지역:</strong> " + escape(text(scope.requestedRegion())) + "<br />"
                + "<strong>정규화 지역:</strong> " + escape(text(scope.normalizedRegion())) + "<br />"
                + "<strong>적용 범위:</strong> " + escape(text(scope.scopeDescription())) + "<br />"
                + "<strong>반경:</strong> " + escape(radius(scope.radiusMeters())) + "</p>";
    }

    private String renderDataSources(List<LocationAnalysisContent.DataSource> sources) {
        if (sources == null || sources.isEmpty()) return muted("데이터 출처 없음");
        String rows = sources.stream().limit(MAX_TABLE_ROWS).map(source -> "<tr><td>" + escape(text(source.id())) + "</td><td>"
                + escape(text(source.type() == null ? null : source.type().name())) + "</td><td>"
                + escape(text(source.source())) + "</td><td>" + escape(text(source.reference())) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>데이터 출처</h3><table><tr><th>ID</th><th>유형</th><th>출처</th><th>참조</th></tr>" + rows + "</table>";
    }

    private String topMetric(List<LocationAnalysisContent.Metric> metrics) {
        LocationAnalysisContent.Metric topByShare = metrics.stream()
                .filter(metric -> metric != null && metric.sharePercent() != null)
                .max(java.util.Comparator.comparing(LocationAnalysisContent.Metric::sharePercent))
                .orElse(null);
        if (topByShare != null) {
            return text(topByShare.label());
        }
        return metrics.stream()
                .filter(metric -> metric != null && metric.value() != null)
                .findFirst()
                .map(metric -> value(metric.value()) + " " + text(metric.unit()))
                .orElse("데이터 없음");
    }

    private String score(Double value) {
        return value == null ? "데이터 없음" : value(value) + "점";
    }

    private String booleanText(Boolean value) {
        return value == null ? "데이터 없음" : value ? "확장됨" : "확장 안 함";
    }

    private String truncationNotice(int actualSize, int displayLimit, String subject) {
        if (actualSize <= displayLimit) {
            return "";
        }
        return muted(subject + "은 상위 " + displayLimit + "건만 표시했습니다. 전체 건수: " + actualSize + "건");
    }

    private String value(Double value) {
        return value == null ? "데이터 없음" : String.format(java.util.Locale.ROOT, "%,.1f", value);
    }

    private String value(Integer value) {
        return value == null ? "데이터 없음" : String.format(java.util.Locale.ROOT, "%,d", value);
    }

    private String radius(Double value) {
        return value == null ? "데이터 없음" : value(value) + "m";
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "데이터 없음" : value;
    }

    private String escape(String value) {
        // OpenHTMLtoPDF는 XHTML(XML) 파서라 &middot; 같은 HTML 전용 named entity를 허용하지 않는다.
        // AI·사용자 입력은 XML 기본 문자와 작은따옴표만 숫자 entity로 치환해 PDF 변환을 안정화한다.
        return (value == null ? "데이터 없음" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ContextFacility(String type, LocationAnalysisContent.Facility facility) {
    }
}
