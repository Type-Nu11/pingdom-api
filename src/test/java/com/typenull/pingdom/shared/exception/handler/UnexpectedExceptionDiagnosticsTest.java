package com.typenull.pingdom.shared.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class UnexpectedExceptionDiagnosticsTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private GlobalExceptionHandler handler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                // MDC가 필터 finally에서 제거되기 전에 실제 로그 시점의 값을 보존합니다.
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        handler = new GlobalExceptionHandler(mock(AuthMetrics.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagnosticController())
                .setControllerAdvice(handler)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
    }

    @Test
    void correlatesRequestAndRecordsSafeDiagnosticsWithoutChangingTheResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/diagnostic-failure")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "trace-1866"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace-1866");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "private-root", "private-token", "private@example.test", "Diagnostics.java");
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMDCPropertyMap()).containsEntry(RequestIdFilter.REQUEST_ID_MDC_KEY, "trace-1866");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("requestId=trace-1866", "root=java.lang.IllegalStateException",
                        "cause=java.lang.IllegalArgumentException", "suppressed[0]=java.lang.IllegalStateException",
                        "example.Diagnostics.handle(Diagnostics.java:41)")
                .doesNotContain("private-root", "private-token", "private@example.test");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeIdsAndDoesNotReuseThePreviousRequestsTrace() throws Exception {
        mockMvc.perform(get("/test/diagnostic-failure")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "previous-trace"))
                .andExpect(status().isInternalServerError());
        appender.list.clear();

        MvcResult invalid = mockMvc.perform(get("/test/diagnostic-failure")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "invalid trace"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String replacement = invalid.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(UUID.fromString(replacement).toString()).isEqualTo(replacement);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();

        MvcResult missing = mockMvc.perform(get("/test/diagnostic-failure"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String generated = missing.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        assertThat(generated).isNotEqualTo(replacement);
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry(RequestIdFilter.REQUEST_ID_MDC_KEY, replacement);
        assertThat(appender.list.get(1).getMDCPropertyMap())
                .containsEntry(RequestIdFilter.REQUEST_ID_MDC_KEY, generated);
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("previous-trace", "invalid trace"));
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void preservesExpectedDomainAndUserConflictResponsesWithoutAddingErrorLogs() {
        assertThat(handler.handleDomainException(new AuthException(AuthErrorCode.INVALID_TOKEN)).getStatusCode())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN.getStatus());
        assertThat(handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException(User.class, 1L)).getStatusCode().value()).isEqualTo(409);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void usesAnExplicitMissingTraceMarkerOutsideServletRequests() {
        assertThat(handler.handleUnexpectedException(new IllegalStateException("private-root"))
                .getStatusCode().value()).isEqualTo(500);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("requestId=-")
                .doesNotContain("private-root");
        assertThat(appender.list.get(0).getThrowableProxy()).isNull();
    }

    @RestController
    static class DiagnosticController {
        @GetMapping("/test/diagnostic-failure")
        void fail() {
            IllegalStateException failure = new IllegalStateException("password=private-root",
                    new IllegalArgumentException("authorization=private-token"));
            failure.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement("example.Diagnostics", "handle", "Diagnostics.java", 41)
            });
            failure.addSuppressed(new IllegalStateException("email=private@example.test"));
            throw failure;
        }
    }
}
