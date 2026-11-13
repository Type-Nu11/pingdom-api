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
     * 최대 시도 횟수 2와 고정 현재 시각을 설정해 이력 생성 및 재시도 한계 전환을 검증할 writer를 생성.
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

    @Test
    void hashesRecipientAndUsesAtomicEmailWriter() {
        writer.record(emailRequest(NotificationDeliveryStatus.SUCCEEDED));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(notificationDeliveryRepository).upsertEmailDelivery(
                eq("SUCCEEDED"), eq(1L), eq(EVENT_ID), eq("EMAIL_VERIFICATION_REQUESTED"), hash.capture(),
                eq("message-id"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq(false), eq(2), eq(LocalDateTime.now(CLOCK)));
        assertThat(hash.getValue()).hasSize(64).doesNotContain("user@example.com");
        // 조회 경로도 동일한 EMAIL:recipient 키를 사용해야 성공 이력을 재사용할 수 있다.
        writer.isEmailDeliveryTerminal(EVENT_ID, "user@example.com");
        org.mockito.Mockito.verify(notificationDeliveryRepository)
                .findDeliveryRecord(EVENT_ID, NotificationDeliveryChannel.EMAIL, hash.getValue());
        org.mockito.Mockito.verify(notificationDeliveryRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void delegatesEmailRetryLimitToAtomicWriter() {
        writer.record(emailRequest(NotificationDeliveryStatus.RETRY_SCHEDULED));

        org.mockito.Mockito.verify(notificationDeliveryRepository).upsertEmailDelivery(
                eq("RETRY_SCHEDULED"), eq(1L), eq(EVENT_ID), eq("EMAIL_VERIFICATION_REQUESTED"), anyString(),
                eq("message-id"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq(false), eq(2), eq(LocalDateTime.now(CLOCK)));
    }

    @Test
    void recognizesSuccessAndStaleAsTerminalButRetriesOtherFailures() {
        NotificationDelivery delivery = NotificationDelivery.create(NotificationDeliveryChannel.EMAIL,
                1L, null, null, EVENT_ID, "EMAIL_VERIFICATION_REQUESTED", "hash", LocalDateTime.now(CLOCK));
        when(notificationDeliveryRepository.findDeliveryRecord(eq(EVENT_ID), eq(NotificationDeliveryChannel.EMAIL), anyString()))
                .thenReturn(Optional.of(delivery));

        delivery.recordResult(NotificationDeliveryStatus.SUCCEEDED, 1L, null, null, "EMAIL_VERIFICATION_REQUESTED",
                "message-id", null, null, null, false, 1, LocalDateTime.now(CLOCK));
        assertThat(writer.isEmailDeliveryTerminal(EVENT_ID, "user@example.com")).isTrue();
        delivery.recordResult(NotificationDeliveryStatus.FAILED, 1L, null, null, "EMAIL_VERIFICATION_REQUESTED",
                null, null, NotificationDeliveryRecorder.ERROR_EMAIL_REQUEST_STALE, "stale", false, 1, LocalDateTime.now(CLOCK));
        assertThat(writer.isEmailDeliveryTerminal(EVENT_ID, "user@example.com")).isTrue();
        delivery.recordResult(NotificationDeliveryStatus.RETRY_SCHEDULED, 1L, null, null, "EMAIL_VERIFICATION_REQUESTED",
                null, null, "SEND_FAILED", "failed", true, 1, LocalDateTime.now(CLOCK));
        assertThat(writer.isEmailDeliveryTerminal(EVENT_ID, "user@example.com")).isFalse();
    }

    private NotificationDeliveryRecordRequest emailRequest(NotificationDeliveryStatus status) {
        return new NotificationDeliveryRecordRequest(NotificationDeliveryChannel.EMAIL, status, 1L, null, null,
                EVENT_ID, "EMAIL_VERIFICATION_REQUESTED", "user@example.com", "message-id", null, null, null, false);
    }

    /**
     * 재시도 가능한 FCM 실패도 Outbox 최대 시도 횟수 2에 도달하면 FINAL_FAILED와 누적 시도 횟수 2를 기록하는지 검증.
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
