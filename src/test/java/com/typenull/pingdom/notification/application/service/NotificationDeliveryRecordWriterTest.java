package com.typenull.pingdom.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryRecordWriterTest {

    private static final String EVENT_ID = "event-id";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    private NotificationDeliveryRecordWriter writer;

    /**
     * 최대 시도 횟수 2와 고정 현재 시각을 설정해 이력 생성 및 재시도 한계 전환을 검증할 writer를 만든다.
     */
    @BeforeEach
    void setUp() {
        writer = new NotificationDeliveryRecordWriter(
                notificationDeliveryRepository,
                new OutboxProperties(
                        10,
                        2,
                        100,
                        100,
                        2,
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5),
                        Duration.ofDays(7)
                ),
                CLOCK
        );
    }

    /**
     * 이메일 성공 이력을 저장하면 수신자 해시가 원문 이메일을 포함하지 않는 64자리이고 성공 상태·시도 횟수 1을 기록하는지 검증한다.
     */
    @Test
    void hashesDeliveryRecipient() {
        when(notificationDeliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.record(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.EMAIL,
                NotificationDeliveryStatus.SUCCEEDED,
                1L,
                null,
                null,
                EVENT_ID,
                "EMAIL_VERIFICATION_REQUESTED",
                "user@example.com",
                "message-id",
                null,
                null,
                null,
                false
        ));

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        org.mockito.Mockito.verify(notificationDeliveryRepository).save(captor.capture());
        NotificationDelivery delivery = captor.getValue();
        assertThat(delivery.getRecipientHash()).hasSize(64);
        assertThat(delivery.getRecipientHash()).doesNotContain("user@example.com");
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    /**
     * 재시도 예약된 이메일의 두 번째 실패는 최대 시도 횟수에 도달해 FINAL_FAILED로 바뀌고 최신 실패 사유를 저장하는지 검증한다.
     */
    @Test
    void finalizesEmailFailureAtAttemptLimit() {
        NotificationDelivery existing = NotificationDelivery.create(
                NotificationDeliveryChannel.EMAIL,
                1L,
                null,
                null,
                EVENT_ID,
                "EMAIL_VERIFICATION_REQUESTED",
                "recipient-hash",
                LocalDateTime.now(CLOCK)
        );
        existing.recordResult(
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                1L,
                null,
                null,
                "EMAIL_VERIFICATION_REQUESTED",
                null,
                "500",
                "POSTMARK_SEND_FAILED",
                "failed",
                true,
                1,
                LocalDateTime.now(CLOCK)
        );

        when(notificationDeliveryRepository.findDeliveryRecord(eq(EVENT_ID), eq(NotificationDeliveryChannel.EMAIL), anyString()))
                .thenReturn(Optional.of(existing));
        when(notificationDeliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.record(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.EMAIL,
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                1L,
                null,
                null,
                EVENT_ID,
                "EMAIL_VERIFICATION_REQUESTED",
                "user@example.com",
                null,
                "500",
                "POSTMARK_SEND_FAILED",
                "failed again",
                true
        ));

        assertThat(existing.getStatus()).isEqualTo(NotificationDeliveryStatus.FINAL_FAILED);
        assertThat(existing.getAttemptCount()).isEqualTo(2);
        assertThat(existing.getFailureReason()).isEqualTo("failed again");
    }

    /**
     * 재시도 가능한 FCM 실패도 Outbox 최대 시도 횟수 2에 도달하면 FINAL_FAILED와 누적 시도 횟수 2를 기록하는지 검증한다.
     */
    @Test
    void finalizesFcmFailureAtAttemptLimit() {
        NotificationDelivery existing = NotificationDelivery.create(
                NotificationDeliveryChannel.FCM,
                1L,
                10L,
                "NEW_LIKE",
                EVENT_ID,
                "MAP_IMAGE_LIKED",
                "recipient-hash",
                LocalDateTime.now(CLOCK)
        );
        existing.recordResult(
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                1L,
                10L,
                "NEW_LIKE",
                "MAP_IMAGE_LIKED",
                null,
                "UNAVAILABLE",
                "FCM_SEND_FAILED",
                "failed",
                true,
                1,
                LocalDateTime.now(CLOCK)
        );

        when(notificationDeliveryRepository.findDeliveryRecord(eq(EVENT_ID), eq(NotificationDeliveryChannel.FCM), anyString()))
                .thenReturn(Optional.of(existing));
        when(notificationDeliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        writer.record(new NotificationDeliveryRecordRequest(
                NotificationDeliveryChannel.FCM,
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                1L,
                10L,
                "NEW_LIKE",
                EVENT_ID,
                "MAP_IMAGE_LIKED",
                "token",
                null,
                "UNAVAILABLE",
                "FCM_SEND_FAILED",
                "failed again",
                true
        ));

        assertThat(existing.getStatus()).isEqualTo(NotificationDeliveryStatus.FINAL_FAILED);
        assertThat(existing.getAttemptCount()).isEqualTo(2);
    }
}
