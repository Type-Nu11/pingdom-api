package com.typenull.pingdom.integration.auth;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * outbox worker를 끄고 관리자 복구 권한, 민감 payload 비노출, 수동 재시도 상태와 감사를 검증.
 */
@Tag("integration")
@SpringBootTest(properties = "outbox.enabled=false")
@AutoConfigureMockMvc
class AdminOutboxEventSecurityIntegrationTest extends AuthRegressionIntegrationTestSupport {

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AdminRoleAssignmentRepository assignmentRepository;
    @Autowired private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    /**
     * 공통 인증 fixture 정리에 더해 이전 수동 복구 감사 로그를 제거.
     */
    @BeforeEach
    void cleanOutboxAuditLogs() {
        adminAuditLogRepository.deleteAllInBatch();
    }

    /**
     * outbox 조회와 수동 재시도 모두 미인증 요청을 INVALID_TOKEN으로 거절하는지 확인.
     */
    @Test
    void outboxRequiresToken() throws Exception {
        mockMvc.perform(get("/admin/outbox-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/admin/outbox-events/event-1/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"checked\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * ANALYST 배정만 가진 관리자의 outbox 조회가 복구 권한 부족으로 거절되는지 확인.
     */
    @Test
    void analystCannotRecover() throws Exception {
        User actor = saveAdmin("outbox-analyst");
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.ANALYST, actor.getId(), LocalDateTime.now()
        ));

        mockMvc.perform(get("/admin/outbox-events")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    /**
     * SUPER_ADMIN이 payload 노출 없이 실패 이벤트를 조회하고 한 번만 재시도 상태로 바꾸며 감사 로그 한 건을 남기는지 확인.
     */
    @Test
    void retryOnceWithAudit() throws Exception {
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

    /**
     * 기간 없는 FAILED 필터 조회와 삭제 후 빈 페이지 메타데이터를 확인.
     */
    @Test
    void failedEventPages() throws Exception {
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

    /**
     * 직접 토큰 발급에 사용할 ADMIN 사용자를 저장하고 flush함. 세부 권한은 각 테스트가 배정.
     */
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

    /**
     * 생성·선점·실패 전이를 거쳐 공급자 오류로 최종 실패한 이메일 이벤트를 저장.
     */
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

    /**
     * 사용자의 현재 ID·이름·역할로 접근 토큰을 직접 발급해 Authorization 헤더 값을 구성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
    }
}
