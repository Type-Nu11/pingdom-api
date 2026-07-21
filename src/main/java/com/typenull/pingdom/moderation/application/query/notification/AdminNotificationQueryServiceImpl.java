package com.typenull.pingdom.moderation.application.query.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationItem;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadAllResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationUnreadCountResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
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
public class AdminNotificationQueryServiceImpl implements AdminNotificationQueryService {

    private final NotificationsRepository notificationsRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationResponse listNotifications(
            Long userId,
            NotificationType type,
            Boolean read,
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

        Page<Notifications> notificationPage = notificationsRepository.findByAdminFilters(
                userId,
                type,
                read,
                from,
                to,
                pageable
        );
        List<AdminNotificationItem> notifications = notificationPage.getContent().stream()
                .map(AdminNotificationItem::from)
                .toList();

        return AdminNotificationResponse.of(
                notifications,
                safePage,
                safeLimit,
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationUnreadCountResponse countUnread() {
        return new AdminNotificationUnreadCountResponse(notificationsRepository.countByIsReadFalse());
    }

    @Override
    @Transactional
    public AdminNotificationReadResponse markAsRead(Long notificationId, Long adminUserId) {
        Notifications notification = notificationsRepository.findById(notificationId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.NOTIFICATION_NOT_FOUND));
        boolean beforeRead = notification.isRead();

        if (!beforeRead) {
            notification.markAsRead();
        }

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.NOTIFICATION_READ,
                AdminAuditTargetType.NOTIFICATION,
                notificationId,
                "관리자 알림 읽음 처리",
                new NotificationReadAuditState(beforeRead),
                new NotificationReadAuditState(notification.isRead())
        );

        return AdminNotificationReadResponse.of(notificationId);
    }

    @Override
    @Transactional
    public AdminNotificationReadAllResponse markAllAsRead(Long adminUserId) {
        long unreadCount = notificationsRepository.countByIsReadFalse();
        int updatedCount = notificationsRepository.markAllAsRead();

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.NOTIFICATION_READ_ALL,
                AdminAuditTargetType.NOTIFICATION,
                "ALL",
                "관리자 전체 알림 읽음 처리",
                new NotificationReadAllAuditState(unreadCount),
                new NotificationReadAllAuditState(0)
        );

        return AdminNotificationReadAllResponse.of(updatedCount);
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_NOTIFICATION_FILTER_PERIOD);
        }
    }

    private record NotificationReadAuditState(boolean read) {
    }

    private record NotificationReadAllAuditState(long unreadCount) {
    }
}
