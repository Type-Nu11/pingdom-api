package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.repository.NotificationSettingRepository;
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

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T14:00:00Z"), ZoneOffset.UTC);
        notificationDeliveryPolicy = new NotificationDeliveryPolicy(notificationSettingRepository, clock);
    }

    @Test
    void missingSettingUsesDefaultAllowPolicy() {
        when(notificationSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        boolean canReceive = notificationDeliveryPolicy.canReceive(USER_ID, NotificationType.NEW_LIKE);

        assertThat(canReceive).isTrue();
    }

    @Test
    void disabledNotificationTypeIsBlocked() {
        NotificationSetting setting = NotificationSetting.createDefault(USER_ID, NOW);
        setting.updateNewLikeEnabled(false, NOW);
        when(notificationSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

        boolean canReceive = notificationDeliveryPolicy.canReceive(USER_ID, NotificationType.NEW_LIKE);

        assertThat(canReceive).isFalse();
    }

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
