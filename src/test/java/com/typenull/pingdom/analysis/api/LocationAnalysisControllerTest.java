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
import com.typenull.pingdom.analysis.application.LocationAnalysisReportAccessPolicy;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class LocationAnalysisControllerTest {

    private static final JwtAuthenticatedUser OWNER = new JwtAuthenticatedUser(1L, "owner");

    @Test
    void exposesArchivedHtmlForPdfDebugging() throws Exception {
        LocationAnalysisReportService reportService = mock(LocationAnalysisReportService.class);
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        LocationAnalysisReportAccessPolicy accessPolicy = accessPolicy();
        when(archiveService.html("report-1", "owner@example.com"))
                .thenReturn("<!doctype html><html lang=\"ko\"><body>보고서</body></html>");
        MockMvc mockMvc = mockMvcBuilder(reportService, archiveService, accessPolicy).build();

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
        LocationAnalysisReportAccessPolicy accessPolicy = accessPolicy();
        when(reportService.generate(any())).thenReturn(new LocationAnalysisReportService.LocationAnalysisPdf(
                new byte[]{'%', 'P', 'D', 'F', '-'},
                "report-1",
                "입지 분석"
        ));
        MockMvc mockMvc = mockMvcBuilder(reportService, archiveService, accessPolicy).build();
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
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"location-analysis-undated-foot-traffic-analysis-v1.pdf\""))
                .andExpect(content().bytes(new byte[]{'%', 'P', 'D', 'F', '-'}));
    }

    @Test
    void returnsJsonBadGatewayWhenAiResponseIsInvalidForPdfAcceptRequest() throws Exception {
        LocationAnalysisReportService reportService = mock(LocationAnalysisReportService.class);
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        LocationAnalysisReportAccessPolicy accessPolicy = accessPolicy();
        when(reportService.generate(any())).thenThrow(new AnalysisReportException(
                AnalysisReportErrorCode.AI_RESPONSE_INVALID, null
        ));
        MockMvc mockMvc = mockMvcBuilder(reportService, archiveService, accessPolicy)
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .build();
        LocationAnalysisRequest request = new LocationAnalysisRequest();
        request.setRegion("서울 강남구");
        request.setCategory("카페");
        request.setEmail("owner@example.com");
        request.setPrivacyConsent(true);

        mockMvc.perform(post("/analysis/reports/location")
                        .accept(MediaType.APPLICATION_PDF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"code":"AI_RESPONSE_INVALID","message":"AI 분석 응답을 처리할 수 없습니다."}
                        """));
    }

    private LocationAnalysisReportAccessPolicy accessPolicy() {
        LocationAnalysisReportAccessPolicy accessPolicy = mock(LocationAnalysisReportAccessPolicy.class);
        when(accessPolicy.requireOwnedEmail(OWNER.userId(), "owner@example.com"))
                .thenReturn("owner@example.com");
        return accessPolicy;
    }

    private StandaloneMockMvcBuilder mockMvcBuilder(
            LocationAnalysisReportService reportService,
            LocationAnalysisReportArchiveService archiveService,
            LocationAnalysisReportAccessPolicy accessPolicy
    ) {
        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return parameter.getParameterType().equals(JwtAuthenticatedUser.class);
            }

            @Override
            public Object resolveArgument(
                    org.springframework.core.MethodParameter parameter,
                    org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                    org.springframework.web.context.request.NativeWebRequest webRequest,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory
            ) {
                return OWNER;
            }
        };
        return MockMvcBuilders
                .standaloneSetup(new LocationAnalysisController(
                        reportService,
                        archiveService,
                        accessPolicy
                ))
                .setCustomArgumentResolvers(currentUserResolver);
    }
}
