package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisDataProvider;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisPromptFactory;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisResponseValidator;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocationAnalysisReportService {

    private final LocationAnalysisPromptFactory promptFactory;
    private final AiAnalysisClient aiAnalysisClient;
    private final LocationAnalysisResponseValidator responseValidator;
    private final LocationAnalysisHtmlComposer htmlComposer;
    private final HtmlToPdfConverter htmlToPdfConverter;
    private final Clock clock;
    private final McpAnalysisDataProvider mcpAnalysisDataProvider;

    public LocationAnalysisReportService(
            LocationAnalysisPromptFactory promptFactory,
            AiAnalysisClient aiAnalysisClient,
            LocationAnalysisResponseValidator responseValidator,
            LocationAnalysisHtmlComposer htmlComposer,
            HtmlToPdfConverter htmlToPdfConverter,
            Clock clock
    ) {
        this(promptFactory, aiAnalysisClient, responseValidator, htmlComposer, htmlToPdfConverter, clock, null);
    }

    @Autowired
    public LocationAnalysisReportService(
            LocationAnalysisPromptFactory promptFactory,
            AiAnalysisClient aiAnalysisClient,
            LocationAnalysisResponseValidator responseValidator,
            LocationAnalysisHtmlComposer htmlComposer,
            HtmlToPdfConverter htmlToPdfConverter,
            Clock clock,
            McpAnalysisDataProvider mcpAnalysisDataProvider
    ) {
        this.promptFactory = promptFactory;
        this.aiAnalysisClient = aiAnalysisClient;
        this.responseValidator = responseValidator;
        this.htmlComposer = htmlComposer;
        this.htmlToPdfConverter = htmlToPdfConverter;
        this.clock = clock;
        this.mcpAnalysisDataProvider = mcpAnalysisDataProvider;
    }

    public LocationAnalysisPdf generate(LocationAnalysisRequest request) {
        LocalDate analysisBasisDate = LocalDate.now(clock);
        String mcpRecommendationJson = mcpAnalysisDataProvider == null
                ? null
                : mcpAnalysisDataProvider.fetch(request.toCriteriaMap());
        AiAnalysisResponse aiResponse = aiAnalysisClient.analyze(
                mcpAnalysisDataProvider == null
                        ? promptFactory.create(request, analysisBasisDate)
                        : promptFactory.create(request, analysisBasisDate, mcpRecommendationJson)
        );
        responseValidator.validate(request, aiResponse);
        String reportId = UUID.randomUUID().toString();
        LocalDate publishedDate = LocalDate.now(clock);
        String html = htmlComposer.compose(
                reportId,
                aiResponse.reportName(),
                publishedDate,
                aiResponse.analysisBasisDate(),
                aiResponse.content()
        );
        byte[] pdf = htmlToPdfConverter.convert(html);
        return new LocationAnalysisPdf(pdf, reportId, aiResponse.reportName());
    }

    public record LocationAnalysisPdf(byte[] content, String reportId, String reportName) {
    }

}
