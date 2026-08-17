package com.typenull.pingdom.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@SpringBootTest(properties = "outbox.enabled=false")
@AutoConfigureMockMvc
class AdminOutboxEventSecurityIntegrationTest extends AuthRegressionIntegrationTestSupport {

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AdminRoleAssignmentRepository assignmentRepository;
    @Autowired private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanOutboxAuditLogs() {
        adminAuditLogRepository.deleteAllInBatch();
    }

    @Test
    void outboxOperationsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/admin/outbox-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/admin/outbox-events/event-1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"checked\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void specializedAdminWithoutRecoveryPermissionIsRejected() throws Exception {
        User actor = saveAdmin("outbox-analyst");
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.ANALYST, actor.getId(), LocalDateTime.now()
        ));

        mockMvc.perform(get("/admin/outbox-events")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    @Test
    void superAdminCanQueryAndRetryFailedEventOnceWithAuditHistory() throws Exception {
        User actor = saveAdmin("outbox-super-admin");
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.SUPER_ADMIN, actor.getId(), LocalDateTime.now()
        ));
        OutboxEvent failed = saveFailedEvent();
        String token = bearerToken(actor);

        mockMvc.perform(get("/admin/outbox-events")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("status", "FAILED")
                        .param("eventType", "EMAIL_VERIFICATION_REQUESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventId").value(failed.getEventId()))
                .andExpect(jsonPath("$.events[0].status").value("FAILED"))
                .andExpect(jsonPath("$.events[0].payload").doesNotExist());

        mockMvc.perform(post("/admin/outbox-events/{eventId}/retry", failed.getEventId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"외부 공급자 장애 복구 확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY"))
                .andExpect(jsonPath("$.attemptCount").value(0));

        mockMvc.perform(post("/admin/outbox-events/{eventId}/retry", failed.getEventId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"중복 요청\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTBOX_EVENT_RETRY_NOT_ALLOWED"));

        OutboxEvent retried = outboxEventRepository.findById(failed.getEventId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(retried.getAttemptCount()).isZero();
        assertThat(adminAuditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getActorUserId()).isEqualTo(actor.getId());
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.OUTBOX_EVENT_RETRIED);
                    assertThat(log.getTargetType()).isEqualTo(AdminAuditTargetType.OUTBOX_EVENT);
                    assertThat(log.getTargetId()).isEqualTo(failed.getEventId());
                });
    }

    @Test
    void superAdminCanQueryFailedEventsWithoutPeriodAndReceiveEmptyPage() throws Exception {
        User actor = saveAdmin("outbox-filter-admin");
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.SUPER_ADMIN, actor.getId(), LocalDateTime.now()
        ));
        OutboxEvent failed = saveFailedEvent();
        String token = bearerToken(actor);

        mockMvc.perform(get("/admin/outbox-events")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("status", "FAILED")
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].eventId").value(failed.getEventId()))
                .andExpect(jsonPath("$.events[0].status").value("FAILED"))
                .andExpect(jsonPath("$.totalCount").value(1));

        outboxEventRepository.deleteAllInBatch();

        mockMvc.perform(get("/admin/outbox-events")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("status", "FAILED")
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    private User saveAdmin(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());
    }

    private OutboxEvent saveFailedEvent() {
        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:admin-recovery",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{\"verificationCode\":\"secret\"}",
                "USER",
                "10",
                now.minusMinutes(1)
        );
        event.claim(now.minusSeconds(30));
        event.fail(now, 1, now, "provider unavailable");
        return outboxEventRepository.saveAndFlush(event);
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
    }
}
