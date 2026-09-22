package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private static final String OUTBOX_EVENT_ID = "outbox-event-id";

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

    /**
     * 알림 저장·기기별 발송·전송 이력의 협력을 검사하도록 모의 의존성과 고정 Clock으로 FCM 서비스를 만든다.
     */
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

    /**
     * 수신 허용 사용자의 두 기기에 좋아요 알림을 발송하면 토큰을 넣지 않은 알림을 한 번 저장하는지 검증한다.
     * 각 토큰의 발송 및 성공 이력이 같은 알림 ID로 기록되는지도 확인한다.
     */
    @Test
    void sendsLikeNotificationToEveryDevice() {
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

    /**
     * 수신 정책이 좋아요 알림을 차단하면 기기 토큰 조회·알림 저장·외부 발송을 모두 생략하는지 검증한다.
     */
    @Test
    void skipsBlockedLikeNotification() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(false);

        fcmService.sendLikeNotification(OWNER_ID, LIKER_ID);

        verify(fcmDeviceTokenService, never()).findTokens(any());
        verify(notificationsRepository, never()).save(any());
        verify(fcmMessageSender, never()).send(any(), any(), any(), any(), any());
    }

    /**
     * FCM이 토큰 무효 오류를 반환하면 호출 밖으로 예외를 전파하지 않고 토큰을 삭제하며 재시도 불가 실패 이력을 기록하는지 검증한다.
     */
    @Test
    void removesInvalidTokenAfterFailure() {
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

    /**
     * 일시적 발송 실패에서도 알림 저장을 호출하고 재시도 가능한 실패 이력을 기록하는지 검증한다.
     * 모의 저장소를 사용하므로 실제 트랜잭션 커밋 여부는 검사하지 않는다.
     */
    @Test
    void recordsRetryableFcmFailure() {
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

    /**
     * 동일 Outbox 이벤트를 재처리하면 기존 알림 ID를 재사용하고 이미 성공한 기기를 제외한 실패 토큰만 다시 발송하는지 검증한다.
     * 첫 결과에는 재시도 가능 실패가 있고 재시도 성공 후에는 없어지는지도 확인한다.
     */
    @Test
    void retriesOnlyFailedDeviceToken() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(userRepository.findById(LIKER_ID)).thenReturn(Optional.of(user(LIKER_ID, "liker")));
        when(notificationDeliveryPolicy.canReceive(OWNER_ID, NotificationType.NEW_LIKE)).thenReturn(true);
        when(fcmDeviceTokenService.findTokens(OWNER_ID)).thenReturn(List.of(
                FcmDeviceToken.create(OWNER_ID, "succeeded-token", LocalDateTime.now()),
                FcmDeviceToken.create(OWNER_ID, "retry-token", LocalDateTime.now())
        ));
        when(notificationDeliveryRecorder.findFcmNotificationId(OUTBOX_EVENT_ID)).thenReturn(null, 100L);
        when(notificationDeliveryRecorder.isFcmDeliverySucceeded(OUTBOX_EVENT_ID, "succeeded-token"))
                .thenReturn(false, true);
        when(notificationsRepository.save(any(Notifications.class))).thenReturn(savedNotification());
        when(fcmMessageSender.send(eq("succeeded-token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L)))
                .thenReturn("sent");
        when(fcmMessageSender.send(eq("retry-token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L)))
                .thenThrow(new FcmSendException("temporary", false, null))
                .thenReturn("sent");

        FcmDispatchResult firstResult = fcmService.sendLikeNotification(OWNER_ID, LIKER_ID, OUTBOX_EVENT_ID);
        FcmDispatchResult retryResult = fcmService.sendLikeNotification(OWNER_ID, LIKER_ID, OUTBOX_EVENT_ID);

        assertThat(firstResult.hasRetryableFailure()).isTrue();
        assertThat(retryResult.hasRetryableFailure()).isFalse();
        verify(notificationsRepository, times(1)).save(any(Notifications.class));
        verify(fcmMessageSender, times(1))
                .send(eq("succeeded-token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L));
        verify(fcmMessageSender, times(2))
                .send(eq("retry-token"), eq(NotificationType.NEW_LIKE), any(), any(), eq(100L));
    }

    /**
     * 좋아요를 누른 사용자와 알림 수신자의 ID·이름을 지정해 발송 시나리오를 준비한다.
     */
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

    /**
     * 기기별 발송과 이력의 공통 참조가 될 저장 완료 알림 ID 100을 제공한다.
     */
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
