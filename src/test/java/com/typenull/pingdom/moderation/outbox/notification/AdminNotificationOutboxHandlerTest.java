package com.typenull.pingdom.moderation.outbox.notification;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCreationService;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminNotificationOutboxHandlerTest {

    @Mock
    private AdminNotificationCreationService creationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminNotificationOutboxHandler handler;

    /**
     * 신고 접수 알림 JSON을 처리하면 유형·이벤트 키·이동 토큰·본문 인자를 생성 서비스에 그대로 전달하는지 검증.
     */
    @Test
    void deserializesPayloadAndCreatesNotifications() throws Exception {
        AdminNotificationOutboxPayload payload = new AdminNotificationOutboxPayload(
                NotificationType.ADMIN_REPORT_RECEIVED,
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                "report:30",
                List.of("30", "12")
        );

        handler.handle("event-id", objectMapper.writeValueAsString(payload));

        verify(creationService).create(
                NotificationType.ADMIN_REPORT_RECEIVED,
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                "report:30",
                List.of("30", "12")
        );
    }
}
