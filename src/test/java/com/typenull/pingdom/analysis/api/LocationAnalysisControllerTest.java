package com.typenull.pingdom.analysis.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocationAnalysisControllerTest {

    @Test
    void exposesArchivedHtmlForPdfDebugging() throws Exception {
        LocationAnalysisReportService reportService = mock(LocationAnalysisReportService.class);
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        when(archiveService.html("report-1", "owner@example.com"))
                .thenReturn("<!doctype html><html lang=\"ko\"><body>보고서</body></html>");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LocationAnalysisController(reportService, archiveService))
                .build();

        mockMvc.perform(get("/analysis/reports/report-1/html")
                        .param("email", "owner@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("보고서")));
    }

    @Test
    void returnsPdfResponseForAnalysisRequest() throws Exception {
        LocationAnalysisReportService reportService = mock(LocationAnalysisReportService.class);
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        when(reportService.generate(any())).thenReturn(new LocationAnalysisReportService.LocationAnalysisPdf(
                new byte[]{'%', 'P', 'D', 'F', '-'},
                "report-1",
                "입지 분석"
        ));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LocationAnalysisController(reportService, archiveService))
                .build();
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("서울 강남구");
        request.setCategory("카페");
        request.setEmail("owner@example.com");
        request.setPrivacyConsent(true);

        mockMvc.perform(post("/analysis/reports/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"location-analysis-report-1.pdf\""))
                .andExpect(content().bytes(new byte[]{'%', 'P', 'D', 'F', '-'}));
    }
}
