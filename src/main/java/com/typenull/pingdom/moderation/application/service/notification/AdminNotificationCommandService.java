package com.typenull.pingdom.moderation.application.service.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadAllResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminNotificationCommandService {

    private final NotificationsRepository notificationsRepository;
    private final AdminAuditLogService adminAuditLogService;

    public AdminNotificationReadResponse markAsRead(Long notificationId, Long adminUserId) {
        Notifications notification = notificationsRepository.findByIdAndUserIdAndTypeIn(
                        notificationId,
                        adminUserId,
                        NotificationType.adminTypes()
                )
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

    public AdminNotificationReadAllResponse markAllAsRead(Long adminUserId) {
        long unreadCount = notificationsRepository.countByUserIdAndTypeInAndIsReadFalse(
                adminUserId,
                NotificationType.adminTypes()
        );
        int updatedCount = notificationsRepository.markAllAdminNotificationsAsRead(
                adminUserId,
                NotificationType.adminTypes()
        );

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

    private record NotificationReadAuditState(boolean read) {
    }

    private record NotificationReadAllAuditState(long unreadCount) {
    }
}
