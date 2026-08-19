package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingResponse;
import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingUpdateRequest;
import com.typenull.pingdom.notification.domain.NotificationSetting;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationSettingRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
/** 사용자별 알림 채널과 수신 설정을 조회·변경합니다. */
public class NotificationSettingService {

    private final UserRepository userRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationSettingResponse getSetting(Long userId) {
        ensureActiveUser(userId);
        return notificationSettingRepository.findByUserId(userId)
                .map(NotificationSettingResponse::from)
                .orElseGet(() -> new NotificationSettingResponse(
                        true,
                        true,
                        false,
                        null,
                        null,
                        NotificationSetting.DEFAULT_TIMEZONE
                ));
    }

    @Transactional
    public NotificationSettingResponse updateSetting(Long userId, NotificationSettingUpdateRequest request) {
        NotificationSetting setting = findOrCreateSetting(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        String timezone = resolveTimezone(setting, request.timezone());
        LocalTime quietHoursStart = request.quietHoursStart() == null
                ? setting.getQuietHoursStart()
                : request.quietHoursStart();
        LocalTime quietHoursEnd = request.quietHoursEnd() == null
                ? setting.getQuietHoursEnd()
                : request.quietHoursEnd();
        boolean quietHoursEnabled = request.quietHoursEnabled() == null
                ? setting.isQuietHoursEnabled()
                : request.quietHoursEnabled();

        validateQuietHours(quietHoursEnabled, quietHoursStart, quietHoursEnd);

        if (request.newHotplaceEnabled() != null) {
            setting.updateNewHotplaceEnabled(request.newHotplaceEnabled(), now);
        }
        if (request.newLikeEnabled() != null) {
            setting.updateNewLikeEnabled(request.newLikeEnabled(), now);
        }
        if (request.quietHoursEnabled() != null) {
            setting.updateQuietHoursEnabled(request.quietHoursEnabled(), now);
        }
        if (request.quietHoursStart() != null || request.quietHoursEnd() != null) {
            setting.updateQuietHours(quietHoursStart, quietHoursEnd, now);
        }
        if (request.timezone() != null) {
            setting.updateTimezone(timezone, now);
        }

        return NotificationSettingResponse.from(setting);
    }

    private NotificationSetting findOrCreateSetting(Long userId) {
        ensureActiveUser(userId);

        return notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(
                        NotificationSetting.createDefault(userId, LocalDateTime.now(clock))
                ));
    }

    private void ensureActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
    }

    private String resolveTimezone(NotificationSetting setting, String timezone) {
        if (timezone == null) {
            return setting.getTimezone();
        }
        String normalizedTimezone = timezone.trim();
        if (!StringUtils.hasText(normalizedTimezone)) {
            throw new NotificationsException(NotificationsErrorCode.INVALID_NOTIFICATION_TIMEZONE);
        }
        try {
            ZoneId.of(normalizedTimezone);
            return normalizedTimezone;
        } catch (DateTimeException exception) {
            throw new NotificationsException(NotificationsErrorCode.INVALID_NOTIFICATION_TIMEZONE, exception);
        }
    }

    private void validateQuietHours(boolean enabled, LocalTime start, LocalTime end) {
        if (!enabled) {
            return;
        }
        if (start == null || end == null || start.equals(end)) {
            throw new NotificationsException(NotificationsErrorCode.INVALID_QUIET_HOURS);
        }
    }
}
