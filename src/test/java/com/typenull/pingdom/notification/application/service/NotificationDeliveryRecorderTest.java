package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryRecorderTest {

    @Mock
    private NotificationDeliveryRecordWriter writer;

    private NotificationDeliveryRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new NotificationDeliveryRecorder(writer);
    }

    @Test
    void recordFcmSuccessMapsNewLikeToMapImageLikedOutboxType() {
        recorder.recordFcmSuccess(
                1L,
                10L,
                NotificationType.NEW_LIKE,
                "outbox-event-id",
                "token",
                "provider-message-id"
        );

        ArgumentCaptor<NotificationDeliveryRecordRequest> captor =
                ArgumentCaptor.forClass(NotificationDeliveryRecordRequest.class);
        verify(writer).record(captor.capture());

        assertThat(captor.getValue().outboxEventType()).isEqualTo("MAP_IMAGE_LIKED");
    }

    @Test
    void recordFcmSuccessDoesNotGuessOutboxTypeForUnmappedNotificationType() {
        recorder.recordFcmSuccess(
                1L,
                10L,
                NotificationType.NEW_HOTPLACE,
                "outbox-event-id",
                "token",
                "provider-message-id"
        );

        ArgumentCaptor<NotificationDeliveryRecordRequest> captor =
                ArgumentCaptor.forClass(NotificationDeliveryRecordRequest.class);
        verify(writer).record(captor.capture());

        assertThat(captor.getValue().outboxEventType()).isNull();
    }

    @Test
    void recordRetryableFcmFailureSchedulesDeliveryRetry() {
        recorder.recordFcmFailure(
                1L,
                10L,
                NotificationType.NEW_LIKE,
                "outbox-event-id",
                "token",
                "UNAVAILABLE",
                NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED,
                "temporary",
                true
        );

        ArgumentCaptor<NotificationDeliveryRecordRequest> captor =
                ArgumentCaptor.forClass(NotificationDeliveryRecordRequest.class);
        verify(writer).record(captor.capture());

        assertThat(captor.getValue().status()).isEqualTo(NotificationDeliveryStatus.RETRY_SCHEDULED);
        assertThat(captor.getValue().retryable()).isTrue();
    }
}
