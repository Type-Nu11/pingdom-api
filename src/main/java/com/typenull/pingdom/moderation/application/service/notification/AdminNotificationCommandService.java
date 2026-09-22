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

/**
 * 본인 소유의 관리자용 알림만 읽음 처리하고 감사 기록을 남김.
 * 단건 재호출은 읽음 상태를 유지해도 감사 기록은 추가. 전체 처리 응답은 사전 조회 건수가 아닌 bulk 갱신 행 수.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminNotificationCommandService {

    private final NotificationsRepository notificationsRepository;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 해당 관리자 소유의 관리자용 알림만 읽음 처리하고 같은 트랜잭션에 감사 기록을 저장.
     * 대상 조건에 맞는 알림이 없으면 NOTIFICATION_NOT_FOUND이며 이미 읽은 알림도 감사 기록은 추가.
     */
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

    /**
     * 해당 관리자의 관리자용 미읽음 알림을 bulk 갱신하고 감사 기록을 같은 트랜잭션에 저장.
     * 응답 건수는 사전 미읽음 조회 수가 아닌 실제 갱신 행 수이며, 감사용 변경 후 건수는 0으로 기록.
     */
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
