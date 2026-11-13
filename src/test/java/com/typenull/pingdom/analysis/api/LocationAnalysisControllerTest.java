package com.typenull.pingdom.analysis.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportAccessPolicy;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportGenerationLimiter;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportPageResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportResponse;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportRepository;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportGenerationProperties;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class LocationAnalysisControllerTest {

    private static final JwtAuthenticatedUser OWNER = new JwtAuthenticatedUser(1L, "owner");

    @Test
    void returnsDefaultSummaryPageWithMetadata() throws Exception {
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        when(archiveService.list(OWNER.userId(), 1, 20))
                .thenReturn(new LocationAnalysisReportPageResponse(List.of(), 1, 20, 0, 0, false));
        MockMvc mockMvc = mockMvcBuilder(mock(LocationAnalysisReportService.class), archiveService, accessPolicy()).build();

        mockMvc.perform(get("/analysis/reports").param("email", "owner@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports").isArray())
                .andExpect(jsonPath("$.reports.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(archiveService).list(OWNER.userId(), 1, 20);
    }

    @Test
    void forwardsExplicitPageAndRetainsSummaryFields() throws Exception {
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        var summary = LocationAnalysisReportResponse.fromSummary("report-1", "보고서", "카페", "서울",
                null, null, "owner@example.com", true, null, null, null);
        when(archiveService.list(OWNER.userId(), 2, 100))
                .thenReturn(new LocationAnalysisReportPageResponse(List.of(summary), 2, 100, 101, 2, false));
        MockMvc mockMvc = mockMvcBuilder(mock(LocationAnalysisReportService.class), archiveService, accessPolicy()).build();

        mockMvc.perform(get("/analysis/reports").param("email", "owner@example.com")
                        .param("page", "2").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].reportId").value("report-1"))
                .andExpect(jsonPath("$.reports[0].downloadPath").value("/analysis/reports/report-1/download"))
                .andExpect(jsonPath("$.reports[0].htmlDebugPath").value("/analysis/reports/report-1/html"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.totalCount").value(101))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.reports[0].pdfContent").doesNotExist())
                .andExpect(jsonPath("$.reports[0].htmlContent").doesNotExist());
        verify(archiveService).list(OWNER.userId(), 2, 100);
    }

    @Test
    void rejectsAnotherEmailBeforeListingReports() throws Exception {
        UserRepository users = mock(UserRepository.class);
        User account = mock(User.class);
        when(account.getEmail()).thenReturn("owner@example.com");
        when(users.findById(OWNER.userId())).thenReturn(Optional.of(account));
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        MockMvc mockMvc = mockMvcBuilder(mock(LocationAnalysisReportService.class), archiveService,
                new LocationAnalysisReportAccessPolicy(users))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();

        mockMvc.perform(get("/analysis/reports").param("email", "other@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_FORBIDDEN"));
        org.mockito.Mockito.verifyNoInteractions(archiveService);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-1,20", "1,0", "1,101", "2147483647,100"})
    void rejectsInvalidPageWithJsonErrorBeforeQuery(int page, int limit) throws Exception {
        LocationAnalysisReportRepository repository = mock(LocationAnalysisReportRepository.class);
        LocationAnalysisReportArchiveService archiveService =
                new LocationAnalysisReportArchiveService(repository, Clock.systemUTC());
        MockMvc mockMvc = mockMvcBuilder(mock(LocationAnalysisReportService.class), archiveService, accessPolicy())
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();

        mockMvc.perform(get("/analysis/reports").param("email", "owner@example.com")
                        .param("page", Integer.toString(page)).param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_INVALID_PAGINATION"));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void rejectsNonNumericPageParameter() throws Exception {
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        MockMvc mockMvc = mockMvcBuilder(mock(LocationAnalysisReportService.class), archiveService, accessPolicy())
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();

        mockMvc.perform(get("/analysis/reports").param("email", "owner@example.com").param("page", "invalid"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(archiveService);
    }

    /**
     * 이메일 소유 정책을 통과한 보관 HTML 조회가 200·text/html과 보고서 내용을 반환하는지 검증.
     */
    @Test
    void returnsArchivedReportHtml() throws Exception {
        LocationAnalysisReportService reportService = mock(LocationAnalysisReportService.class);
        LocationAnalysisReportArchiveService archiveService = mock(LocationAnalysisReportArchiveService.class);
        LocationAnalysisReportAccessPolicy accessPolicy = accessPolicy();
        when(archiveService.html("report-1", OWNER.userId()))
                .thenReturn("<!doctype html><html lang=\"ko\"><body>보고서</body></html>");
        MockMvc mockMvc = mockMvcBuilder(reportService, archiveService, accessPolicy).build();

        mockMvc.perform(get("/analysis/reports/report-1/html")
                        .param("email", "owner@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("보고서")));
        verify(archiveService).html("report-1", OWNER.userId());
    }

    /**
     * 동의와 본인 이메일을 포함한 생성 요청이 200·application/pdf·고정 다운로드 파일명·PDF 바이트를 반환하는지 검증.
     */
    @Test
    void returnsAnalysisPdfAttachment() throws Exception {
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
        verify(archiveService).archive(org.mockito.ArgumentMatchers.eq(OWNER.userId()), any(), any());
    }

    /**
     * Accept가 application/pdf인 요청도 AI 응답 오류 시 502 JSON과 AI_RESPONSE_INVALID 코드·메시지를 반환하는지 검증.
     */
    @Test
    void returnsJsonForInvalidPdfAnalysis() throws Exception {
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

    /**
     * 고정 사용자 1의 owner@example.com 요청을 허용하는 접근 정책 mock을 제공.
     */
    private LocationAnalysisReportAccessPolicy accessPolicy() {
        LocationAnalysisReportAccessPolicy accessPolicy = mock(LocationAnalysisReportAccessPolicy.class);
        when(accessPolicy.requireOwnedEmail(OWNER.userId(), "owner@example.com"))
                .thenReturn("owner@example.com");
        return accessPolicy;
    }

    /**
     * 보고서 생성·보관·접근 정책 대역과 고정 인증 사용자 resolver를 연결한 컨트롤러 MockMvc builder를 제공.
     */
    private StandaloneMockMvcBuilder mockMvcBuilder(
            LocationAnalysisReportService reportService,
            LocationAnalysisReportArchiveService archiveService,
            LocationAnalysisReportAccessPolicy accessPolicy
    ) {
        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            /**
             * JwtAuthenticatedUser 타입 인자만 고정 사용자 resolver의 대상으로 선택.
             */
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return parameter.getParameterType().equals(JwtAuthenticatedUser.class);
            }

            /**
             * 실제 인증 절차 대신 OWNER 객체를 반환해 컨트롤러의 응답 처리만 분리.
             */
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
                        accessPolicy,
                        new LocationAnalysisReportGenerationLimiter(
                                new LocationAnalysisReportGenerationProperties(2, 4)
                        )
                ))
                .setCustomArgumentResolvers(currentUserResolver);
    }
}
