package com.typenull.pingdom.moderation.outbox.notification;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminNotificationOutboxPublisher {

    private static final String EVENT_KEY_PREFIX = "ADMIN_NOTIFICATION:";

    private final OutboxEventPublisher outboxEventPublisher;

    public void publishReportReceived(Long reportId, Long postId) {
        String eventKey = EVENT_KEY_PREFIX + "REPORT_RECEIVED:" + reportId;
        publish(
                eventKey,
                NotificationType.ADMIN_REPORT_RECEIVED,
                "report:" + reportId,
                List.of(String.valueOf(reportId), String.valueOf(postId)),
                "POST_REPORT",
                reportId
        );
    }

    public void publishReportProcessed(Long reportId, Long postId, PostReportStatus status) {
        String eventKey = EVENT_KEY_PREFIX + "REPORT_PROCESSED:" + reportId;
        publish(
                eventKey,
                NotificationType.ADMIN_REPORT_PROCESSED,
                "report:" + reportId,
                List.of(String.valueOf(postId), String.valueOf(reportId), reportStatusLabel(status)),
                "POST_REPORT",
                reportId
        );
    }

    public void publishDuplicatePlaceDetected(Long candidateId, Long leftPlaceId, Long rightPlaceId) {
        String eventKey = EVENT_KEY_PREFIX + "DUPLICATE_PLACE_DETECTED:" + candidateId;
        publish(
                eventKey,
                NotificationType.ADMIN_DUPLICATE_PLACE_DETECTED,
                "place:" + leftPlaceId,
                List.of(String.valueOf(leftPlaceId), String.valueOf(rightPlaceId)),
                "PLACE_DUPLICATE_CANDIDATE",
                candidateId
        );
    }

    public void publishUserSanction(Long sanctionId, Long targetUserId, UserSanctionAction action) {
        String eventKey = EVENT_KEY_PREFIX + "USER_SANCTION:" + sanctionId;
        publish(
                eventKey,
                NotificationType.ADMIN_USER_SANCTION,
                "sanction:" + sanctionId,
                List.of(String.valueOf(targetUserId), sanctionActionLabel(action), String.valueOf(sanctionId)),
                "USER_SANCTION",
                sanctionId
        );
    }

    private void publish(
            String eventKey,
            NotificationType type,
            String token,
            List<String> bodyArguments,
            String aggregateType,
            Long aggregateId
    ) {
        outboxEventPublisher.publish(
                eventKey,
                OutboxEventType.ADMIN_NOTIFICATION_REQUESTED,
                new AdminNotificationOutboxPayload(type, eventKey, token, bodyArguments),
                aggregateType,
                String.valueOf(aggregateId)
        );
    }

    private String reportStatusLabel(PostReportStatus status) {
        return switch (status) {
            case ACCEPTED -> "수락";
            case DECLINED -> "기각";
            default -> status.name();
        };
    }

    private String sanctionActionLabel(UserSanctionAction action) {
        return switch (action) {
            case APPLIED -> "적용";
            case RELEASED -> "해제";
            case EXPIRED -> "만료";
        };
    }
}
