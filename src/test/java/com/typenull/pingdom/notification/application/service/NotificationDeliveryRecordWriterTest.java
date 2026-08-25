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

    @Test
    void recordHashesRecipientWithoutStoringRawRecipient() {
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

    @Test
    void retryScheduledFailureBecomesFinalFailureOnMaxAttempt() {
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

    @Test
    void retryableFcmFailureBecomesFinalFailureOnOutboxMaxAttempt() {
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
