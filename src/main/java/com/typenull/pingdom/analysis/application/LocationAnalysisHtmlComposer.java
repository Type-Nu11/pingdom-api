package com.typenull.pingdom.analysis.application;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class LocationAnalysisHtmlComposer {

    public String compose(
            String reportId,
            String reportName,
            java.time.LocalDate publishedDate,
            java.time.LocalDate analysisBasisDate,
            String aiHtml
    ) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    @page { size: A4; margin: 20mm 16mm; }
                    body { font-family: sans-serif; color: #1f2937; line-height: 1.6; }
                    h1 { color: #0f766e; margin-bottom: 4px; }
                    h2 { border-bottom: 1px solid #d1d5db; padding-bottom: 4px; margin-top: 24px; }
                    .meta { color: #6b7280; font-size: 12px; }
                  </style>
                </head>
                <body>
                  <h1>%s</h1>
                  <p class="meta">보고서 ID: %s<br>발행일자: %s<br>분석 기준일: %s</p>
                  %s
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(reportName),
                HtmlUtils.htmlEscape(reportId),
                publishedDate,
                analysisBasisDate,
                aiHtml
        );
    }
}
