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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationQueryServiceImpl implements AdminNotificationQueryService {

    private final NotificationsRepository notificationsRepository;

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
