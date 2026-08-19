package com.typenull.pingdom.moderation.application.query.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationDeliveryItem;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationDeliveryResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminNotificationDeliveryQueryServiceImpl implements AdminNotificationDeliveryQueryService {

    private final NotificationDeliveryRepository notificationDeliveryRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationDeliveryResponse listDeliveries(
            Long userId,
            NotificationDeliveryChannel channel,
            NotificationDeliveryStatus status,
            NotificationType notificationType,
            OutboxEventType outboxEventType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    ) {
        validatePeriod(from, to);

        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<NotificationDelivery> deliveryPage = notificationDeliveryRepository.findByFilters(
                userId,
                channel,
                status,
                notificationType == null ? null : notificationType.name(),
                outboxEventType == null ? null : outboxEventType.name(),
                from != null,
                from,
                to != null,
                to,
                pageable
        );
        List<AdminNotificationDeliveryItem> deliveries = deliveryPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return AdminNotificationDeliveryResponse.of(
                deliveries,
                safePage,
                safeLimit,
                deliveryPage.getTotalElements(),
                deliveryPage.getTotalPages()
        );
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_NOTIFICATION_DELIVERY_FILTER_PERIOD);
        }
    }

    private AdminNotificationDeliveryItem toItem(NotificationDelivery delivery) {
        return new AdminNotificationDeliveryItem(
                delivery.getId(),
                delivery.getChannel(),
                delivery.getStatus(),
                delivery.getUserId(),
                delivery.getNotificationId(),
                delivery.getNotificationType(),
                delivery.getOutboxEventId(),
                delivery.getOutboxEventType(),
                delivery.getProviderMessageId(),
                delivery.getProviderErrorCode(),
                delivery.getErrorCode(),
                delivery.getFailureReason(),
                delivery.isRetryable(),
                delivery.getAttemptCount(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
