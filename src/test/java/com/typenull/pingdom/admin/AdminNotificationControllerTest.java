package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        notificationsRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listNotificationsFiltersByUserTypeReadAndPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("notificationAdmin", UserRole.ADMIN);
        saveNotification(
                10L,
                NotificationType.ADMIN_REPORT_RECEIVED,
                "신고 접수 알림",
                "새로운 신고가 접수되었습니다.",
                "report:1",
                false,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
        saveNotification(
                11L,
                NotificationType.NEW_LIKE,
                "좋아요 알림",
                "normalUser님이 좋아요를 눌렀어요",
                "post:2",
                true,
                LocalDateTime.of(2026, 7, 21, 11, 0)
        );

        mockMvc.perform(get("/admin/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("userId", "10")
                        .param("type", NotificationType.ADMIN_REPORT_RECEIVED.name())
                        .param("read", "false")
                        .param("from", "2026-07-21T00:00:00")
                        .param("to", "2026-07-21T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].userId").value(10))
                .andExpect(jsonPath("$.notifications[0].type").value(NotificationType.ADMIN_REPORT_RECEIVED.name()))
                .andExpect(jsonPath("$.notifications[0].read").value(false))
                .andExpect(jsonPath("$.notifications[0].token").value("report:1"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void countUnreadNotifications() throws Exception {
        String adminAccessToken = createUserAndLogin("notificationCountAdmin", UserRole.ADMIN);
        saveNotification(10L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:1", false, LocalDateTime.now());
        saveNotification(11L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:2", false, LocalDateTime.now());
        saveNotification(12L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:3", true, LocalDateTime.now());

        mockMvc.perform(get("/admin/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void markNotificationAsReadRecordsAuditLog() throws Exception {
        String adminAccessToken = createUserAndLogin("notificationReadAdmin", UserRole.ADMIN);
        Notifications notification = saveNotification(
                10L,
                NotificationType.ADMIN_DUPLICATE_PLACE_DETECTED,
                "중복 장소 알림",
                "중복 의심 장소가 발견되었습니다.",
                "place:1",
                false,
                LocalDateTime.now()
        );

        mockMvc.perform(patch("/admin/notifications/{notificationId}/read", notification.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notification.getId()))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."));

        Notifications updated = notificationsRepository.findById(notification.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.isRead()).isTrue();
        org.assertj.core.api.Assertions.assertThat(adminAuditLogRepository.findAll())
                .anySatisfy(log -> {
                    org.assertj.core.api.Assertions.assertThat(log.getAction()).isEqualTo(AdminAuditAction.NOTIFICATION_READ);
                    org.assertj.core.api.Assertions.assertThat(log.getTargetType()).isEqualTo(AdminAuditTargetType.NOTIFICATION);
                    org.assertj.core.api.Assertions.assertThat(log.getTargetId()).isEqualTo(String.valueOf(notification.getId()));
                });
    }

    @Test
    void markAllNotificationsAsRead() throws Exception {
        String adminAccessToken = createUserAndLogin("notificationReadAllAdmin", UserRole.ADMIN);
        saveNotification(10L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:1", false, LocalDateTime.now());
        saveNotification(11L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:2", false, LocalDateTime.now());
        saveNotification(12L, NotificationType.NEW_LIKE, "좋아요 알림", "좋아요", "post:3", true, LocalDateTime.now());

        mockMvc.perform(patch("/admin/notifications/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.message").value("전체 알림을 읽음 처리했습니다."));

        org.assertj.core.api.Assertions.assertThat(notificationsRepository.countByIsReadFalse()).isZero();
        org.assertj.core.api.Assertions.assertThat(adminAuditLogRepository.findAll())
                .anySatisfy(log -> org.assertj.core.api.Assertions.assertThat(log.getAction())
                        .isEqualTo(AdminAuditAction.NOTIFICATION_READ_ALL));
    }

    @Test
    void listNotificationsRejectsInvalidPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("notificationPeriodAdmin", UserRole.ADMIN);

        mockMvc.perform(get("/admin/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("from", "2026-07-22T00:00:00")
                        .param("to", "2026-07-21T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_FILTER_PERIOD"));
    }

    @Test
    void listNotificationsRejectsNonAdminUser() throws Exception {
        String userAccessToken = createUserAndLogin("notificationNormalUser", UserRole.USER);

        mockMvc.perform(get("/admin/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Notifications saveNotification(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String token,
            boolean read,
            LocalDateTime createdAt
    ) {
        return notificationsRepository.save(Notifications.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .token(token)
                .isRead(read)
                .createdAt(createdAt)
                .build());
    }

    private String createUserAndLogin(String username, UserRole role) throws Exception {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());

        LoginRequest loginRequest = new LoginRequest(username, "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
