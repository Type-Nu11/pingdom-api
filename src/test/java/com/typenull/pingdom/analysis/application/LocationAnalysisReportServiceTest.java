package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisPromptFactory;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisResponseValidator;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationAnalysisReportServiceTest {

    @Test
    void sendsPromptToAiComposesMetadataAndConvertsHtmlToPdf() {
        LocationAnalysisPromptFactory promptFactory = mock(LocationAnalysisPromptFactory.class);
        AiAnalysisClient aiClient = mock(AiAnalysisClient.class);
        LocationAnalysisResponseValidator validator = mock(LocationAnalysisResponseValidator.class);
        LocationAnalysisCompetitionService competitionService = mock(LocationAnalysisCompetitionService.class);
        LocationAnalysisHtmlComposer htmlComposer = mock(LocationAnalysisHtmlComposer.class);
        HtmlToPdfConverter pdfConverter = mock(HtmlToPdfConverter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("서울 강남구");
        AiAnalysisPrompt prompt = new AiAnalysisPrompt("prompt", java.time.LocalDate.of(2026, 8, 18));

        when(promptFactory.create(request, java.time.LocalDate.of(2026, 8, 18))).thenReturn(prompt);
        when(aiClient.analyze(prompt)).thenReturn(new AiAnalysisResponse(
                new LocationAnalysisContent(
                        "입지 분석",
                        new LocationAnalysisContent.OverallLocationEvaluation(
                                LocationAnalysisContent.Grade.CONDITIONAL, "분석", List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.TargetPopulationAnalysis(
                                "분석", List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.FootTrafficAnalysis(
                                "분석", 1d, List.of(), List.of(), List.of()
                        ),
                        new LocationAnalysisContent.NearbyFacilities(List.of(), List.of(), List.of(), List.of()),
                        new LocationAnalysisContent.AnalysisScope(
                                "서울 강남구", "서울특별시 강남구", LocationAnalysisContent.ScopeLevel.DISTRICT,
                                "구 전체", null
                        ),
                        List.of(), List.of()
                ),
                java.time.LocalDate.of(2026, 8, 18)
        ));
        when(htmlComposer.compose(any(), any(), any(), any(), any())).thenReturn("<html/> ");
        when(pdfConverter.convert("<html/> ")).thenReturn(new byte[]{'%', 'P', 'D', 'F', '-'});
        when(competitionService.enrich(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        LocationAnalysisReportService service = new LocationAnalysisReportService(
                promptFactory, aiClient, validator, competitionService, htmlComposer, pdfConverter, clock
        );

        LocationAnalysisReportService.LocationAnalysisPdf result = service.generate(request);

        assertThat(result.content()).startsWith(new byte[]{'%', 'P', 'D', 'F', '-'});
        assertThat(result.reportName()).isEqualTo("입지 분석");
        verify(pdfConverter).convert("<html/> ");
    }
}
