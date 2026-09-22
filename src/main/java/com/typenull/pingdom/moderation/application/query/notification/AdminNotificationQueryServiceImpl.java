package com.typenull.pingdom.moderation.application.query.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationItem;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationUnreadCountResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 관리자에게 속한 관리자 알림 유형만 목록·미읽음 건수로 제공합니다.
 * 다른 관리자의 알림이나 일반 사용자용 알림은 관리자 계정이라는 이유만으로 포함하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationQueryServiceImpl implements AdminNotificationQueryService {

    private final NotificationsRepository notificationsRepository;

    /**
     * 해당 관리자 소유의 관리자용 알림만 종류·읽음 여부·기간으로 좁혀 최신순 반환합니다.
     * 역전된 기간은 거절하며 page는 1 이상·limit는 1~100으로 보정합니다.
     */
    @Override
    public AdminNotificationResponse listNotifications(
            Long adminUserId,
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
                adminUserId,
                NotificationType.adminTypes(),
                type,
                read,
                from != null,
                from,
                to != null,
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
    public AdminNotificationUnreadCountResponse countUnread(Long adminUserId) {
        return new AdminNotificationUnreadCountResponse(
                notificationsRepository.countByUserIdAndTypeInAndIsReadFalse(
                        adminUserId,
                        NotificationType.adminTypes()
                )
        );
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_NOTIFICATION_FILTER_PERIOD);
        }
    }
}
