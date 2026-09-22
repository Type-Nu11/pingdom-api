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

/** 사용자별 알림 채널과 수신 설정을 조회·변경. */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final UserRepository userRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final Clock clock;

    /**
     * 사용자의 존재·탈퇴 여부를 확인하고 저장된 수신 설정을 반환.
     * 설정이 없으면 새 핫플·좋아요 알림 허용, 방해금지 해제 및 기본 시간대 응답을 만들고 DB 저장은 생략.
     */
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

    /**
     * null 필드는 기존 값을 유지하는 부분 변경. 시간 값의 null 전달로 기존 구간을 지울 수는 없음.
     * 방해금지를 켠 결과가 유효한지 전체 조합을 검사한 뒤 명시된 값만 반영.
     */
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
