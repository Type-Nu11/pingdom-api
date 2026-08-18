package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
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
class AdminNotificationDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        notificationDeliveryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listDeliveriesFiltersByUserChannelStatusTypeAndPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("deliveryAdmin", UserRole.ADMIN);
        saveDelivery(
                10L,
                NotificationDeliveryChannel.FCM,
                NotificationDeliveryStatus.SUCCEEDED,
                NotificationType.NEW_LIKE.name(),
                OutboxEventType.MAP_IMAGE_LIKED.name(),
                "event-1",
                "message-1",
                null,
                null,
                LocalDateTime.of(2026, 6, 25, 10, 0)
        );
        saveDelivery(
                11L,
                NotificationDeliveryChannel.EMAIL,
                NotificationDeliveryStatus.FINAL_FAILED,
                null,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED.name(),
                "event-2",
                null,
                "POSTMARK_SEND_FAILED",
                "failed",
                LocalDateTime.of(2026, 6, 25, 11, 0)
        );

        mockMvc.perform(get("/admin/notification-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("userId", "10")
                        .param("channel", NotificationDeliveryChannel.FCM.name())
                        .param("status", NotificationDeliveryStatus.SUCCEEDED.name())
                        .param("notificationType", NotificationType.NEW_LIKE.name())
                        .param("from", "2026-06-25T00:00:00")
                        .param("to", "2026-06-25T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries.length()").value(1))
                .andExpect(jsonPath("$.deliveries[0].channel").value(NotificationDeliveryChannel.FCM.name()))
                .andExpect(jsonPath("$.deliveries[0].status").value(NotificationDeliveryStatus.SUCCEEDED.name()))
                .andExpect(jsonPath("$.deliveries[0].userId").value(10))
                .andExpect(jsonPath("$.deliveries[0].notificationType").value(NotificationType.NEW_LIKE.name()))
                .andExpect(jsonPath("$.deliveries[0].providerMessageId").value("message-1"))
                .andExpect(jsonPath("$.deliveries[0].recipientHash").doesNotExist())
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void listDeliveriesSupportsStatusFilterWithoutPeriodAndEmptyResult() throws Exception {
        String adminAccessToken = createUserAndLogin("deliveryStatusAdmin", UserRole.ADMIN);
        saveDelivery(
                10L,
                NotificationDeliveryChannel.FCM,
                NotificationDeliveryStatus.FAILED,
                NotificationType.NEW_LIKE.name(),
                OutboxEventType.MAP_IMAGE_LIKED.name(),
                "event-status-filter",
                null,
                "FCM_SEND_FAILED",
                "failed",
                LocalDateTime.of(2026, 8, 16, 12, 0)
        );

        mockMvc.perform(get("/admin/notification-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("status", NotificationDeliveryStatus.FAILED.name())
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries.length()").value(1))
                .andExpect(jsonPath("$.deliveries[0].status").value(NotificationDeliveryStatus.FAILED.name()))
                .andExpect(jsonPath("$.totalCount").value(1));

        mockMvc.perform(get("/admin/notification-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("status", NotificationDeliveryStatus.RETRY_SCHEDULED.name())
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listDeliveriesRejectsInvalidPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("deliveryPeriodAdmin", UserRole.ADMIN);

        mockMvc.perform(get("/admin/notification-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("from", "2026-06-26T00:00:00")
                        .param("to", "2026-06-25T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_DELIVERY_FILTER_PERIOD"));
    }

    @Test
    void listDeliveriesRejectsNonAdminUser() throws Exception {
        String userAccessToken = createUserAndLogin("deliveryNormalUser", UserRole.USER);

        mockMvc.perform(get("/admin/notification-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private void saveDelivery(
            Long userId,
            NotificationDeliveryChannel channel,
            NotificationDeliveryStatus status,
            String notificationType,
            String outboxEventType,
            String outboxEventId,
            String providerMessageId,
            String errorCode,
            String failureReason,
            LocalDateTime createdAt
    ) {
        NotificationDelivery delivery = NotificationDelivery.create(
                channel,
                userId,
                100L,
                notificationType,
                outboxEventId,
                outboxEventType,
                "recipient-hash-" + outboxEventId,
                createdAt
        );
        delivery.recordResult(
                status,
                userId,
                100L,
                notificationType,
                outboxEventType,
                providerMessageId,
                null,
                errorCode,
                failureReason,
                status == NotificationDeliveryStatus.RETRY_SCHEDULED,
                1,
                createdAt
        );
        notificationDeliveryRepository.save(delivery);
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
