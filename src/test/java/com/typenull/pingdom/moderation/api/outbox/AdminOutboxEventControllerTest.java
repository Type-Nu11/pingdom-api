package com.typenull.pingdom.moderation.api.outbox;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventItem;
import com.typenull.pingdom.moderation.api.dto.outbox.AdminOutboxEventResponse;
import com.typenull.pingdom.moderation.application.query.outbox.AdminOutboxEventQueryService;
import com.typenull.pingdom.moderation.application.service.outbox.AdminOutboxEventRecoveryService;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class AdminOutboxEventControllerTest {

    @Mock private AdminOutboxEventQueryService queryService;
    @Mock private AdminOutboxEventRecoveryService recoveryService;

    private MockMvc mockMvc;

    /**
     * 관리자 Outbox HTTP 계약을 검사하도록 조회·복구 서비스와 고정 관리자 인증 인자를 연결한다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOutboxEventController(queryService, recoveryService))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    /**
                     * CurrentUser 인자만 테스트용 관리자 인증 객체로 해석한다.
                     */
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUser.class);
                    }

                    /**
                     * 조회 및 재시도 서비스로 전달할 관리자 사용자 ID를 10으로 고정한다.
                     */
                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer container,
                            NativeWebRequest request,
                            WebDataBinderFactory binderFactory
                    ) {
                        return new JwtAuthenticatedUser(10L, "admin");
                    }
                })
                .build();
    }

    /**
     * 상태·유형·집계 필터로 조회한 응답에는 이벤트 ID와 실패 상태를 담고 payload는 노출하지 않는지 검증한다.
     * 사유를 전달한 재시도 응답이 RETRY와 시도 횟수 0을 반환하는지도 확인한다.
     */
    @Test
    void exposesFilteredOutboxAndRetry() throws Exception {
        AdminOutboxEventItem failed = item(OutboxEventStatus.FAILED, 5, "provider failed");
        AdminOutboxEventItem retried = item(OutboxEventStatus.RETRY, 0, null);
        when(queryService.list(
                eq(10L),
                eq(OutboxEventStatus.FAILED),
                eq(OutboxEventType.EMAIL_VERIFICATION_REQUESTED),
                eq("USER"),
                eq("10"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                eq(1),
                eq(20)
        )).thenReturn(AdminOutboxEventResponse.of(List.of(failed), 1, 20, 1, 1));
        when(recoveryService.retry(10L, "event-1", "provider recovered")).thenReturn(retried);

        mockMvc.perform(get("/admin/outbox-events")
                        .param("status", "FAILED")
                        .param("eventType", "EMAIL_VERIFICATION_REQUESTED")
                        .param("aggregateType", "USER")
                        .param("aggregateId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.events[0].status").value("FAILED"))
                .andExpect(jsonPath("$.events[0].payload").doesNotExist());

        mockMvc.perform(post("/admin/outbox-events/event-1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"provider recovered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY"))
                .andExpect(jsonPath("$.attemptCount").value(0));
    }

    /**
     * 공백뿐인 수동 재시도 사유는 요청 검증에서 400으로 거절되는지 검증한다.
     */
    @Test
    void rejectsBlankRetryReason() throws Exception {
        mockMvc.perform(post("/admin/outbox-events/event-1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 컨트롤러 응답 비교를 위해 같은 이벤트의 상태·시도 횟수·오류만 달리한 항목을 만든다.
     */
    private AdminOutboxEventItem item(OutboxEventStatus status, int attemptCount, String lastError) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 10, 0);
        return new AdminOutboxEventItem(
                "event-1",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "USER",
                "10",
                status,
                attemptCount,
                now,
                null,
                null,
                lastError,
                now.minusHours(1),
                now
        );
    }
}
