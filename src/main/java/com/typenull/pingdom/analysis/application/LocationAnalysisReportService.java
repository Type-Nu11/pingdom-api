package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisPromptFactory;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LocationAnalysisReportService {

    private final LocationAnalysisPromptFactory promptFactory;
    private final AiAnalysisClient aiAnalysisClient;
    private final LocationAnalysisHtmlComposer htmlComposer;
    private final HtmlToPdfConverter htmlToPdfConverter;
    private final Clock clock;

    public LocationAnalysisPdf generate(LocationAnalysisRequest request) {
        LocalDate analysisBasisDate = LocalDate.now(clock);
        AiAnalysisResponse aiResponse = aiAnalysisClient.analyze(
                promptFactory.create(request, analysisBasisDate)
        );
        validate(aiResponse);
        String reportId = UUID.randomUUID().toString();
        LocalDate publishedDate = LocalDate.now(clock);
        String html = htmlComposer.compose(
                reportId,
                aiResponse.reportName(),
                publishedDate,
                aiResponse.analysisBasisDate(),
                aiResponse.html()
        );
        byte[] pdf = htmlToPdfConverter.convert(html);
        return new LocationAnalysisPdf(pdf, reportId, aiResponse.reportName());
    }

    public record LocationAnalysisPdf(byte[] content, String reportId, String reportName) {
    }

    private void validate(AiAnalysisResponse response) {
        if (response == null
                || !StringUtils.hasText(response.reportName())
                || response.analysisBasisDate() == null
                || !StringUtils.hasText(response.html())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
    }
}
