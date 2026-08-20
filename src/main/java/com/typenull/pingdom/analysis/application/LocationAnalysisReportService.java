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
        LocalDate analysisBasisDate = LocalDate.now(clock);
        AiAnalysisResponse aiResponse = aiAnalysisClient.analyze(
                promptFactory.create(request, analysisBasisDate)
        );
        if (aiResponse.hasHtmlReport()) {
            responseValidator.validateHtml(aiResponse);
        } else {
            responseValidator.validate(request, aiResponse);
        }
        String reportId = UUID.randomUUID().toString();
        LocalDate publishedDate = LocalDate.now(clock);
        String html = aiResponse.hasHtmlReport()
                ? aiResponse.htmlReport()
                : htmlComposer.compose(
                        reportId,
                        aiResponse.reportName(),
                        publishedDate,
                        aiResponse.analysisBasisDate(),
                        aiResponse.content()
                );
        log.info("입지 분석 PDF 변환 전 HTML 원본입니다. reportId={}\n{}", reportId, html);
        byte[] pdf = htmlToPdfConverter.convert(html);
        return new LocationAnalysisPdf(pdf, reportId, aiResponse.reportName());
    }

    public record LocationAnalysisPdf(byte[] content, String reportId, String reportName) {
    }

}
