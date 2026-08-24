package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisPromptFactory;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisResponseValidator;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LocationAnalysisReportService {

    private final LocationAnalysisPromptFactory promptFactory;
    private final AiAnalysisClient aiAnalysisClient;
    private final LocationAnalysisResponseValidator responseValidator;
    private final LocationAnalysisHtmlComposer htmlComposer;
    private final HtmlToPdfConverter htmlToPdfConverter;
    private final Clock clock;

    public LocationAnalysisReportService(
            LocationAnalysisPromptFactory promptFactory,
            AiAnalysisClient aiAnalysisClient,
            LocationAnalysisResponseValidator responseValidator,
            LocationAnalysisHtmlComposer htmlComposer,
            HtmlToPdfConverter htmlToPdfConverter,
            Clock clock
    ) {
        this.promptFactory = promptFactory;
        this.aiAnalysisClient = aiAnalysisClient;
        this.responseValidator = responseValidator;
        this.htmlComposer = htmlComposer;
        this.htmlToPdfConverter = htmlToPdfConverter;
        this.clock = clock;
    }

    public LocationAnalysisPdf generate(LocationAnalysisRequest request) {
        long startedAt = System.nanoTime();
        LocalDate analysisBasisDate = LocalDate.now(clock);
        AiAnalysisResponse aiResponse = aiAnalysisClient.analyze(
                promptFactory.create(request, analysisBasisDate)
        );
        long aiCompletedAt = System.nanoTime();
        if (aiResponse.hasHtmlReport()) {
            responseValidator.validateHtml(aiResponse);
        } else {
            responseValidator.validate(request, aiResponse);
        }
        String reportId = UUID.randomUUID().toString();
        LocalDate publishedDate = LocalDate.now(clock);
        LocalDate effectiveAnalysisBasisDate = aiResponse.analysisBasisDate() == null
                ? analysisBasisDate : aiResponse.analysisBasisDate();
        String html = aiResponse.hasHtmlReport()
                ? aiResponse.htmlReport()
                : htmlComposer.compose(
                        reportId,
                        aiResponse.reportName(),
                        publishedDate,
                        effectiveAnalysisBasisDate,
                        aiResponse.content()
                );
        byte[] pdf = htmlToPdfConverter.convert(html);
        long completedAt = System.nanoTime();
        log.info(
                "입지 분석 보고서 생성 완료. reportId={}, aiMs={}, pdfMs={}, htmlLength={}, pdfBytes={}",
                reportId,
                elapsedMillis(startedAt, aiCompletedAt),
                elapsedMillis(aiCompletedAt, completedAt),
                html.length(),
                pdf.length
        );
        return new LocationAnalysisPdf(
                pdf, reportId, aiResponse.reportName(), html, publishedDate, effectiveAnalysisBasisDate
        );
    }

    private long elapsedMillis(long startedAt, long completedAt) {
        return (completedAt - startedAt) / 1_000_000;
    }

    public record LocationAnalysisPdf(
            byte[] content,
            String reportId,
            String reportName,
            String html,
            LocalDate publishedDate,
            LocalDate analysisBasisDate
    ) {
        public LocationAnalysisPdf(byte[] content, String reportId, String reportName) {
            this(content, reportId, reportName, null, null, null);
        }
    }

}
