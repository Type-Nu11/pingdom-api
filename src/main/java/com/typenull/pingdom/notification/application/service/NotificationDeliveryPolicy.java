package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.repository.NotificationSettingRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryPolicy {

    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean canReceive(Long userId, NotificationType type) {
        return notificationSettingRepository.findByUserId(userId)
                .map(setting -> canReceive(setting, type))
                .orElse(true);
    }

    private boolean canReceive(NotificationSetting setting, NotificationType type) {
        if (!setting.isEnabled(type)) {
            return false;
        }
        return !isInQuietHours(setting);
    }

    private boolean isInQuietHours(NotificationSetting setting) {
        if (!setting.isQuietHoursEnabled()
                || setting.getQuietHoursStart() == null
                || setting.getQuietHoursEnd() == null
                || setting.getQuietHoursStart().equals(setting.getQuietHoursEnd())) {
            return false;
        }

        LocalTime now = LocalTime.now(clock.withZone(resolveZoneId(setting)));
        LocalTime start = setting.getQuietHoursStart();
        LocalTime end = setting.getQuietHoursEnd();

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private ZoneId resolveZoneId(NotificationSetting setting) {
        try {
            return ZoneId.of(setting.getTimezone());
        } catch (DateTimeException exception) {
            log.warn(
                    "알림 설정 timezone이 유효하지 않아 기본 timezone을 사용합니다. userId={}, timezone={}",
                    setting.getUserId(),
                    setting.getTimezone()
            );
            return ZoneId.of(NotificationSetting.DEFAULT_TIMEZONE);
        }
    }
}
