package com.typenull.pingdom.moderation.outbox.notification;

import static org.mockito.Mockito.verify;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminNotificationOutboxPublisherTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private AdminNotificationOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AdminNotificationOutboxPublisher(outboxEventPublisher);
    }

    @Test
    void publishesDeterministicEventsForEveryAdminNotificationType() {
        publisher.publishReportReceived(30L, 12L);
        publisher.publishReportProcessed(30L, 12L, PostReportStatus.ACCEPTED);
        publisher.publishDuplicatePlaceDetected(40L, 7L, 8L);
        publisher.publishUserSanction(50L, 9L, UserSanctionAction.EXPIRED);

        verify(outboxEventPublisher).publish(
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                OutboxEventType.ADMIN_NOTIFICATION_REQUESTED,
                new AdminNotificationOutboxPayload(
                        NotificationType.ADMIN_REPORT_RECEIVED,
                        "ADMIN_NOTIFICATION:REPORT_RECEIVED:30",
                        "report:30",
                        List.of("30", "12")
                ),
                "POST_REPORT",
                "30"
        );
        verify(outboxEventPublisher).publish(
                "ADMIN_NOTIFICATION:REPORT_PROCESSED:30",
                OutboxEventType.ADMIN_NOTIFICATION_REQUESTED,
                new AdminNotificationOutboxPayload(
                        NotificationType.ADMIN_REPORT_PROCESSED,
                        "ADMIN_NOTIFICATION:REPORT_PROCESSED:30",
                        "report:30",
                        List.of("12", "30", "수락")
                ),
                "POST_REPORT",
                "30"
        );
        verify(outboxEventPublisher).publish(
                "ADMIN_NOTIFICATION:DUPLICATE_PLACE_DETECTED:40",
                OutboxEventType.ADMIN_NOTIFICATION_REQUESTED,
                new AdminNotificationOutboxPayload(
                        NotificationType.ADMIN_DUPLICATE_PLACE_DETECTED,
                        "ADMIN_NOTIFICATION:DUPLICATE_PLACE_DETECTED:40",
                        "place:7",
                        List.of("7", "8")
                ),
                "PLACE_DUPLICATE_CANDIDATE",
                "40"
        );
        verify(outboxEventPublisher).publish(
                "ADMIN_NOTIFICATION:USER_SANCTION:50",
                OutboxEventType.ADMIN_NOTIFICATION_REQUESTED,
                new AdminNotificationOutboxPayload(
                        NotificationType.ADMIN_USER_SANCTION,
                        "ADMIN_NOTIFICATION:USER_SANCTION:50",
                        "sanction:50",
                        List.of("9", "만료", "50")
                ),
                "USER_SANCTION",
                "50"
        );
    }
}
