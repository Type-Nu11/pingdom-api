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

    /**
     * 채널별 이력 요청 매핑만 검사하도록 저장 writer를 대체한 recorder를 구성한다.
     */
    @BeforeEach
    void setUp() {
        recorder = new NotificationDeliveryRecorder(writer);
    }

    /**
     * 좋아요 FCM 성공을 기록하면 writer 요청의 Outbox 유형이 MAP_IMAGE_LIKED로 매핑되는지 검증한다.
     */
    @Test
    void mapsLikeNotificationEventType() {
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

    /**
     * 명시적으로 연결되지 않은 신규 핫플 알림은 이력 요청의 Outbox 유형을 null로 두는지 검증한다.
     */
    @Test
    void leavesUnmappedEventTypeEmpty() {
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

    /**
     * 재시도 가능한 FCM 실패를 기록하면 writer에 RETRY_SCHEDULED 상태와 retryable true를 전달하는지 검증한다.
     */
    @Test
    void schedulesRetryableFcmFailure() {
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
