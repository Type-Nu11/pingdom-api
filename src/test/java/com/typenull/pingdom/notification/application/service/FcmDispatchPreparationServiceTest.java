package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.application.service.FcmDispatchPreparationService.PreparedNotification;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class FcmDispatchPreparationServiceTest {

    private static final String EVENT_ID = "event-id";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository users;
    @Mock private NotificationsRepository notifications;
    @Mock private FcmDeviceTokenService tokens;
    @Mock private NotificationDeliveryPolicy policy;
    @Mock private NotificationDeliveryRecorder recorder;

    private FcmDispatchPreparationService service;

    @BeforeEach
    void setUp() {
        service = new FcmDispatchPreparationService(users, notifications, tokens, policy, recorder, CLOCK);
    }

    @Test
    void skipsSelfLikeWithoutDatabaseWork() {
        assertThat(service.prepareLike(1L, 1L, EVENT_ID)).isEmpty();
        verifyNoInteractions(users, notifications, tokens, policy, recorder);
    }

    @Test
    void skipsMissingOrWithdrawnRecipient() {
        when(users.findById(1L)).thenReturn(Optional.empty(),
                Optional.of(User.builder().id(1L).status(com.typenull.pingdom.identity.domain.UserStatus.WITHDRAWN)
                        .withdrawnAt(LocalDateTime.now(CLOCK)).build()));

        assertThat(service.prepareLike(1L, 2L, EVENT_ID)).isEmpty();
        assertThat(service.prepareLike(1L, 2L, EVENT_ID)).isEmpty();
        verify(users, never()).findById(2L);
        verifyNoInteractions(notifications, tokens, policy, recorder);
    }

    @Test
    void skipsMissingSender() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "owner")));
        when(users.findById(2L)).thenReturn(Optional.empty());

        assertThat(service.prepareLike(1L, 2L, EVENT_ID)).isEmpty();
        verifyNoInteractions(notifications, tokens, policy, recorder);
    }

    @Test
    void skipsBlockedPolicyBeforeTokenLookup() {
        eligibleUsers();
        when(policy.canReceive(1L, NotificationType.NEW_LIKE)).thenReturn(false);

        assertThat(service.prepareLike(1L, 2L, EVENT_ID)).isEmpty();
        verifyNoInteractions(tokens, notifications, recorder);
    }

    @Test
    void skipsWhenNoTokensRemain() {
        eligibleUsers();
        when(policy.canReceive(1L, NotificationType.NEW_LIKE)).thenReturn(true);
        when(tokens.findTokens(1L)).thenReturn(List.of());

        assertThat(service.prepareLike(1L, 2L, EVENT_ID)).isEmpty();
        verifyNoInteractions(notifications, recorder);
    }

    @Test
    void createsFreshNotificationForEachCallWithoutEventId() {
        eligibleLike();
        when(notifications.save(any())).thenReturn(notification(100L, NotificationType.NEW_LIKE),
                notification(101L, NotificationType.NEW_LIKE));

        PreparedNotification first = service.prepareLike(1L, 2L, null).orElseThrow();
        PreparedNotification second = service.prepareLike(1L, 2L, "").orElseThrow();

        assertThat(first.notificationId()).isEqualTo(100L);
        assertThat(second.notificationId()).isEqualTo(101L);
        assertThat(first.tokens()).containsExactly("newer", "older");
        ArgumentCaptor<Notifications> captor = ArgumentCaptor.forClass(Notifications.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(n -> {
            assertThat(n.getEventKey()).isNull();
            assertThat(n.getToken()).isNull();
        });
        verifyNoInteractions(recorder);
    }

    @Test
    void reusesPreparedNotificationAfterRestartWithoutAnyDeliveryRecord() {
        eligibleLike();
        Notifications saved = notification(100L, NotificationType.NEW_LIKE);
        when(notifications.findByUserIdAndEventKey(1L, "fcm:" + EVENT_ID))
                .thenReturn(Optional.empty(), Optional.of(saved));

        PreparedNotification first = service.prepareLike(1L, 2L, EVENT_ID).orElseThrow();
        PreparedNotification restarted = service.prepareLike(1L, 2L, EVENT_ID).orElseThrow();

        assertThat(first.notificationId()).isEqualTo(restarted.notificationId()).isEqualTo(100L);
        assertThat(first.body()).isEqualTo("stored body");
        verify(notifications).insertFcmNotificationIfAbsent(eq(1L), eq("NEW_LIKE"), any(), any(),
                eq("fcm:" + EVENT_ID), eq(LocalDateTime.now(CLOCK)));
        verify(notifications, never()).save(any());
        verify(recorder).findFcmNotificationId(EVENT_ID);
    }

    @Test
    void reusesLegacyNotificationOnlyForSameUserAndType() {
        eligibleLike();
        when(recorder.findFcmNotificationId(EVENT_ID)).thenReturn(99L);
        when(notifications.findByIdAndUserIdAndTypeIn(99L, 1L, List.of(NotificationType.NEW_LIKE)))
                .thenReturn(Optional.of(notification(99L, NotificationType.NEW_LIKE)));

        assertThat(service.prepareLike(1L, 2L, EVENT_ID).orElseThrow().notificationId()).isEqualTo(99L);
        verify(notifications, never()).insertFcmNotificationIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recreatesMissingOrMismatchedLegacyNotification() {
        eligibleLike();
        when(recorder.findFcmNotificationId(EVENT_ID)).thenReturn(99L);
        when(notifications.findByIdAndUserIdAndTypeIn(99L, 1L, List.of(NotificationType.NEW_LIKE)))
                .thenReturn(Optional.empty());
        when(notifications.findByUserIdAndEventKey(1L, "fcm:" + EVENT_ID))
                .thenReturn(Optional.empty(), Optional.of(notification(100L, NotificationType.NEW_LIKE)));

        assertThat(service.prepareLike(1L, 2L, EVENT_ID).orElseThrow().notificationId()).isEqualTo(100L);
        verify(notifications).insertFcmNotificationIfAbsent(eq(1L), eq("NEW_LIKE"), any(), any(), any(), any());
    }

    @Test
    void rejectsReusedEventWithDifferentType() {
        eligibleLike();
        when(notifications.findByUserIdAndEventKey(1L, "fcm:" + EVENT_ID))
                .thenReturn(Optional.of(notification(100L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER)));

        assertThatThrownBy(() -> service.prepareLike(1L, 2L, EVENT_ID))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(recorder);
    }

    @Test
    void validatesReverificationTypeBeforeCreatingNotification() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "owner")));

        assertThatThrownBy(() -> service.preparePlaceInformationReverification(
                1L, NotificationType.NEW_LIKE, "place", EVENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notifications, tokens, policy, recorder);
    }

    @Test
    void preparesBothSupportedReverificationTypes() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "owner")));
        when(tokens.findTokens(1L)).thenReturn(List.of(
                FcmDeviceToken.create(1L, "token", LocalDateTime.now(CLOCK))));
        when(policy.canReceive(1L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED)).thenReturn(true);
        when(policy.canReceive(1L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER)).thenReturn(true);
        when(notifications.save(any())).thenAnswer(invocation -> {
            Notifications input = invocation.getArgument(0);
            return notification(100L, input.getType());
        });

        for (NotificationType type : List.of(NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED,
                NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER)) {
            PreparedNotification prepared = service.preparePlaceInformationReverification(
                    1L, type, "place", null).orElseThrow();
            assertThat(prepared.type()).isEqualTo(type);
            assertThat(prepared.tokens()).containsExactly("token");
        }
    }

    @Test
    void snapshotsTokenValuesWithoutExposingMutableList() {
        List<String> source = new ArrayList<>(List.of("first"));
        PreparedNotification prepared = new PreparedNotification(
                1L, NotificationType.NEW_LIKE, "title", "body", 100L, EVENT_ID, source);
        source.add("second");

        assertThat(prepared.tokens()).containsExactly("first");
        assertThatThrownBy(() -> prepared.tokens().add("third")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void commitsAndReleasesPreparationConnectionBeforeSending() throws Exception {
        eligibleLike();
        when(notifications.findByUserIdAndEventKey(1L, "fcm:" + EVENT_ID))
                .thenReturn(Optional.of(notification(100L, NotificationType.NEW_LIKE)));
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        ProxyFactory preparationProxy = new ProxyFactory(service);
        preparationProxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        FcmMessageSender sender = mock(FcmMessageSender.class);
        when(sender.send(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
            return "sent";
        });
        FcmService orchestrator = new FcmService(
                (FcmDispatchPreparationService) preparationProxy.getProxy(), tokens, sender, recorder);

        orchestrator.sendLikeNotification(1L, 2L, EVENT_ID);

        InOrder order = inOrder(connection, sender);
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(sender).send("newer", NotificationType.NEW_LIKE, "stored title", "stored body", 100L);
    }

    private void eligibleUsers() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "owner")));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, "liker")));
    }

    private void eligibleLike() {
        eligibleUsers();
        when(policy.canReceive(1L, NotificationType.NEW_LIKE)).thenReturn(true);
        when(tokens.findTokens(1L)).thenReturn(List.of(
                FcmDeviceToken.create(1L, "newer", LocalDateTime.now(CLOCK)),
                FcmDeviceToken.create(1L, "older", LocalDateTime.now(CLOCK).minusMinutes(1))));
    }

    private User user(long id, String username) {
        return User.builder().id(id).username(username).email(username + "@example.com").password("password")
                .birthYear(1998).language("ko").country("KR").build();
    }

    private Notifications notification(long id, NotificationType type) {
        return Notifications.builder().id(id).userId(1L).type(type).title("stored title")
                .body("stored body").createdAt(LocalDateTime.now(CLOCK)).isRead(false).build();
    }
}
