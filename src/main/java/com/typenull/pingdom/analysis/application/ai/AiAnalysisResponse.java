package com.typenull.pingdom.analysis.application.ai;

import java.time.LocalDate;

/** AI 또는 MCP 어댑터가 반환해야 하는 최소 보고서 계약이다. */
public record AiAnalysisResponse(
        LocationAnalysisContent content,
        LocalDate analysisBasisDate
) {

    public String reportName() {
        return content == null ? null : content.reportName();
    }
}
