package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long LIKER_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationsRepository notificationsRepository;

    @Mock
    private FcmDeviceTokenService fcmDeviceTokenService;

    @Mock
    private NotificationDeliveryPolicy notificationDeliveryPolicy;

    @Mock
    private FcmMessageSender fcmMessageSender;

    @Mock
    private NotificationDeliveryRecorder notificationDeliveryRecorder;

    private FcmService fcmService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);
        fcmService = new FcmService(
                userRepository,
                notificationsRepository,
                fcmDeviceTokenService,
                notificationDeliveryPolicy,
                fcmMessageSender,
                notificationDeliveryRecorder,
                clock
        );
    }

    @Test
    void sendLikeNotificationStoresOneNotificationAndSendsToAllDeviceTokens() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(true);
        when(fcmDeviceTokenService.findTokens(OWNER_ID)).thenReturn(List.of(
                FcmDeviceToken.create(OWNER_ID, "token-1", LocalDateTime.now()),
                FcmDeviceToken.create(OWNER_ID, "token-2", LocalDateTime.now())
        ));
        when(notificationsRepository.save(any(Notifications.class))).thenReturn(savedNotification());
        when(fcmMessageSender.send(any(), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L)))
                .thenReturn("sent");

        fcmService.sendLikeNotification(OWNER_ID, LIKER_ID);

        ArgumentCaptor<Notifications> notificationCaptor = ArgumentCaptor.forClass(Notifications.class);
        verify(notificationsRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getToken()).isNull();
        verify(fcmMessageSender).send(eq("token-1"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L));
        verify(fcmMessageSender).send(eq("token-2"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L));
        verify(notificationDeliveryRecorder).recordFcmSuccess(
                eq(OWNER_ID),
                eq(100L),
                eq(NotificationType.NEW_LIKE),
                eq(null),
                eq("token-1"),
                eq("sent")
        );
        verify(notificationDeliveryRecorder).recordFcmSuccess(
                eq(OWNER_ID),
                eq(100L),
                eq(NotificationType.NEW_LIKE),
                eq(null),
                eq("token-2"),
                eq("sent")
        );
    }

    @Test
    void sendLikeNotificationSkipsWhenSettingBlocksType() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(false);

        fcmService.sendLikeNotification(OWNER_ID, LIKER_ID);

        verify(fcmDeviceTokenService, never()).findTokens(any());
        verify(notificationsRepository, never()).save(any());
        verify(fcmMessageSender, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void invalidTokenIsDeletedAndDoesNotFailOutboxHandling() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(true);
        when(fcmDeviceTokenService.findTokens(OWNER_ID)).thenReturn(List.of(
                FcmDeviceToken.create(OWNER_ID, "invalid-token", LocalDateTime.now())
        ));
        when(notificationsRepository.save(any(Notifications.class))).thenReturn(savedNotification());
        when(fcmMessageSender.send(eq("invalid-token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L)))
                .thenThrow(new FcmSendException("invalid", true, null));

        fcmService.sendLikeNotification(OWNER_ID, LIKER_ID);

        verify(fcmDeviceTokenService).deleteInvalidToken("invalid-token");
        verify(notificationDeliveryRecorder).recordFcmFailure(
                eq(OWNER_ID),
                eq(100L),
                eq(NotificationType.NEW_LIKE),
                eq(null),
                eq("invalid-token"),
                eq(null),
                eq(NotificationDeliveryRecorder.ERROR_FCM_INVALID_TOKEN),
                eq("invalid"),
                eq(false)
        );
    }

    @Test
    void transientSendFailureDoesNotRollbackNotificationDispatch() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(true);
        when(fcmDeviceTokenService.findTokens(OWNER_ID)).thenReturn(List.of(
                FcmDeviceToken.create(OWNER_ID, "token", LocalDateTime.now())
        ));
        when(notificationsRepository.save(any(Notifications.class))).thenReturn(savedNotification());
        when(fcmMessageSender.send(eq("token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L)))
                .thenThrow(new FcmSendException("temporary", false, null));

        fcmService.sendLikeNotification(OWNER_ID, LIKER_ID);

        verify(notificationsRepository).save(any(Notifications.class));
        verify(notificationDeliveryRecorder).recordFcmFailure(
                eq(OWNER_ID),
                eq(100L),
                eq(NotificationType.NEW_LIKE),
                eq(null),
                eq("token"),
                eq(null),
                eq(NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED),
                eq("temporary"),
                eq(true)
        );
    }

    private User user(Long userId, String username) {
        return User.builder()
                .id(userId)
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build();
    }

    private Notifications savedNotification() {
        return Notifications.builder()
                .id(100L)
                .userId(OWNER_ID)
                .type(NotificationType.NEW_LIKE)
                .title("좋아요 알림")
                .body("liker님이 좋아요를 눌렀어요")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
