package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationSettingRepository;
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
/** 알림 유형과 사용자 설정을 대조해 전달 가능 채널을 결정합니다. */
public class NotificationDeliveryPolicy {

    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    // 사용자 설정과 알림 유형을 조회해 전달 가능 여부를 판단합니다.
    public boolean canReceive(Long userId, NotificationType type) {
        return notificationSettingRepository.findByUserId(userId)
                .map(setting -> canReceive(setting, type))
                .orElse(true);
    }

    // 설정의 알림 유형별 수신 허용 여부와 방해금지 시간을 함께 평가합니다.
    private boolean canReceive(NotificationSetting setting, NotificationType type) {
        if (!setting.isEnabled(type)) {
            return false;
        }
        return !isInQuietHours(setting);
    }

    // 사용자 시간대 기준 현재 시각이 방해금지 시간에 포함되는지 확인합니다.
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

    // 잘못된 시간대 설정은 기본 시간대인 UTC로 대체합니다.
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
