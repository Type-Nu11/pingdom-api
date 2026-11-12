package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.notification.application.service.FcmDispatchPreparationService.PreparedNotification;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    private static final String EVENT_ID = "event-id";

    @Mock private FcmDispatchPreparationService preparationService;
    @Mock private FcmDeviceTokenService tokenService;
    @Mock private FcmMessageSender sender;
    @Mock private NotificationDeliveryRecorder recorder;

    private FcmService service;

    @BeforeEach
    void setUp() {
        service = new FcmService(preparationService, tokenService, sender, recorder);
    }

    @Test
    void sendsPreparedTokensInOrderWithSameNotification() {
        when(preparationService.prepareLike(1L, 2L, null)).thenReturn(Optional.of(prepared(null, "first", "second")));
        when(sender.send(any(), any(), any(), any(), any())).thenReturn("sent");

        assertThat(service.sendLikeNotification(1L, 2L).notificationsId()).isEqualTo(100L);

        InOrder order = inOrder(sender, recorder);
        order.verify(sender).send("first", NotificationType.NEW_LIKE, "title", "body", 100L);
        order.verify(recorder).recordFcmSuccess(1L, 100L, NotificationType.NEW_LIKE, null, "first", "sent");
        order.verify(sender).send("second", NotificationType.NEW_LIKE, "title", "body", 100L);
        order.verify(recorder).recordFcmSuccess(1L, 100L, NotificationType.NEW_LIKE, null, "second", "sent");
    }

    @Test
    void skipsWhenPreparationIsEmpty() {
        when(preparationService.prepareLike(1L, 2L, EVENT_ID)).thenReturn(Optional.empty());

        assertThat(service.sendLikeNotification(1L, 2L, EVENT_ID)).isEqualTo(FcmDispatchResult.skipped());

        verifyNoInteractions(sender, recorder, tokenService);
    }

    @Test
    void neverSendsWhenPreparationFails() {
        when(preparationService.prepareLike(1L, 2L, EVENT_ID))
                .thenThrow(new IllegalStateException("prepare failed"));

        assertThatThrownBy(() -> service.sendLikeNotification(1L, 2L, EVENT_ID))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(sender, recorder, tokenService);
    }

    @Test
    void removesInvalidTokenWithoutRetryingIt() {
        when(preparationService.prepareLike(1L, 2L, EVENT_ID))
                .thenReturn(Optional.of(prepared(EVENT_ID, "invalid")));
        when(sender.send(any(), any(), any(), any(), any()))
                .thenThrow(new FcmSendException("invalid", true, null));

        assertThat(service.sendLikeNotification(1L, 2L, EVENT_ID).hasRetryableFailure()).isFalse();

        verify(tokenService).deleteInvalidToken("invalid");
        verify(recorder).recordFcmFailure(1L, 100L, NotificationType.NEW_LIKE, EVENT_ID, "invalid", null,
                NotificationDeliveryRecorder.ERROR_FCM_INVALID_TOKEN, "invalid", false);
    }

    @Test
    void retriesOnlyFailedDeviceToken() {
        when(preparationService.prepareLike(1L, 2L, EVENT_ID))
                .thenReturn(Optional.of(prepared(EVENT_ID, "success", "retry")));
        when(recorder.isFcmDeliverySucceeded(EVENT_ID, "success")).thenReturn(false, true);
        when(sender.send(eq("success"), any(), any(), any(), any())).thenReturn("sent");
        when(sender.send(eq("retry"), any(), any(), any(), any()))
                .thenThrow(new FcmSendException("temporary", false, null)).thenReturn("sent");

        assertThat(service.sendLikeNotification(1L, 2L, EVENT_ID).hasRetryableFailure()).isTrue();
        assertThat(service.sendLikeNotification(1L, 2L, EVENT_ID).hasRetryableFailure()).isFalse();

        verify(sender, times(1)).send(eq("success"), any(), any(), any(), eq(100L));
        verify(sender, times(2)).send(eq("retry"), any(), any(), any(), eq(100L));
        verify(tokenService, never()).deleteInvalidToken(any());
    }

    @Test
    void continuesAfterUnexpectedProviderFailure() {
        when(preparationService.prepareLike(1L, 2L, EVENT_ID))
                .thenReturn(Optional.of(prepared(EVENT_ID, "broken", "healthy")));
        when(sender.send(eq("broken"), any(), any(), any(), any())).thenThrow(new IllegalStateException("timeout"));
        when(sender.send(eq("healthy"), any(), any(), any(), any())).thenReturn("sent");

        assertThat(service.sendLikeNotification(1L, 2L, EVENT_ID).hasRetryableFailure()).isTrue();

        verify(recorder).recordFcmFailure(1L, 100L, NotificationType.NEW_LIKE, EVENT_ID, "broken", null,
                NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED, "timeout", true);
        verify(sender).send("healthy", NotificationType.NEW_LIKE, "title", "body", 100L);
    }

    @Test
    void delegatesPlaceReverificationPreparation() {
        when(preparationService.preparePlaceInformationReverification(
                1L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED, "place", EVENT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.sendPlaceInformationReverificationNotification(
                1L, NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED, "place", EVENT_ID))
                .isEqualTo(FcmDispatchResult.skipped());
        verifyNoInteractions(sender);
    }

    @Test
    void rejectsCallerTransactionBeforePreparationOrSending() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        FcmService proxied = (FcmService) factory.getProxy();

        assertThatThrownBy(() -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> proxied.sendLikeNotification(1L, 2L, EVENT_ID)))
                .isInstanceOf(IllegalTransactionStateException.class);

        verifyNoInteractions(preparationService, sender, recorder, tokenService);
        verify(connection).rollback();
        verify(connection).close();
    }

    private PreparedNotification prepared(String eventId, String... tokens) {
        return new PreparedNotification(1L, NotificationType.NEW_LIKE, "title", "body", 100L, eventId, List.of(tokens));
    }
}
