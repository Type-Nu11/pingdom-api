package com.typenull.pingdom.analysis.application.ai;

import java.time.LocalDate;

/** AI 또는 MCP 어댑터가 반환해야 하는 최소 보고서 계약이다. */
public record AiAnalysisResponse(
        LocationAnalysisContent content,
        LocalDate analysisBasisDate,
        String htmlReport,
        String htmlReportName
) {

    public AiAnalysisResponse(LocationAnalysisContent content, LocalDate analysisBasisDate) {
        this(content, analysisBasisDate, null, content == null ? null : content.reportName());
    }

    public AiAnalysisResponse(String htmlReportName, String htmlReport, LocalDate analysisBasisDate) {
        this(null, analysisBasisDate, htmlReport, htmlReportName);
    }

    public String reportName() {
        return htmlReportName != null ? htmlReportName : content == null ? null : content.reportName();
    }

    public boolean hasHtmlReport() {
        return htmlReport != null && !htmlReport.isBlank();
    }
}
