package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryPolicyTest {

    private static final long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-06-25T00:00:00");

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    private NotificationDeliveryPolicy notificationDeliveryPolicy;

    /**
     * 서울 시간으로 23시인 UTC 시각에 고정해 자정을 넘는 방해 금지 구간을 판정할 정책을 만든다.
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T14:00:00Z"), ZoneOffset.UTC);
        notificationDeliveryPolicy = new NotificationDeliveryPolicy(notificationSettingRepository, clock);
    }

    /**
     * 사용자 설정이 없으면 기본 정책에 따라 좋아요 알림 수신을 허용하는지 검증한다.
     */
    @Test
    void missingSettingUsesDefaultAllowPolicy() {
        when(notificationSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        boolean canReceive = notificationDeliveryPolicy.canReceive(USER_ID, NotificationType.NEW_LIKE);

        assertThat(canReceive).isTrue();
    }

    /**
     * 좋아요 알림을 비활성화한 사용자는 해당 유형을 수신할 수 없는지 검증한다.
     */
    @Test
    void disabledNotificationTypeIsBlocked() {
        NotificationSetting setting = NotificationSetting.createDefault(USER_ID, NOW);
        setting.updateNewLikeEnabled(false, NOW);
        when(notificationSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

        boolean canReceive = notificationDeliveryPolicy.canReceive(USER_ID, NotificationType.NEW_LIKE);

        assertThat(canReceive).isFalse();
    }

    /**
     * 서울 기준 22시부터 다음 날 8시까지 방해 금지를 켜면 현재 23시의 좋아요 알림을 차단하는지 검증한다.
     */
    @Test
    void quietHoursAcrossMidnightBlocksNotification() {
        NotificationSetting setting = NotificationSetting.createDefault(USER_ID, NOW);
        setting.updateQuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0), NOW);
        setting.updateQuietHoursEnabled(true, NOW);
        when(notificationSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

        boolean canReceive = notificationDeliveryPolicy.canReceive(USER_ID, NotificationType.NEW_LIKE);

        assertThat(canReceive).isFalse();
    }
}
