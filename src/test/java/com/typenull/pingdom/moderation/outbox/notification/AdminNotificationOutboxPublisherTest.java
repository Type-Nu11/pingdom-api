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

    /**
     * 발행 인자의 이벤트 계약을 검사하도록 모의 Outbox 발행기를 연결.
     */
    @BeforeEach
    void setUp() {
        publisher = new AdminNotificationOutboxPublisher(outboxEventPublisher);
    }

    /**
     * 신고 접수·처리, 장소 중복 탐지, 제재 만료마다 결정된 이벤트 키와 알림 페이로드·집계 유형·집계 ID로 Outbox를 발행하는지 검증.
     */
    @Test
    void publishesAdminNotificationEventContracts() {
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
