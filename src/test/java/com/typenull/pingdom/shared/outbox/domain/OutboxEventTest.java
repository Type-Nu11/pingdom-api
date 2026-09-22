package com.typenull.pingdom.shared.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 22, 12, 0);

    /**
     * 첫 실패에서 RETRY·시도 1회·지정 재시도 시각·오류 메시지가 기록되는지 검증한다.
     */
    @Test
    void failureSchedulesRetryWithFailureDetails() {
        OutboxEvent event = createClaimedEvent();

        event.fail(NOW, 5, NOW.plusSeconds(10), "temporary failure");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(event.getLastError()).isEqualTo("temporary failure");
    }

    /**
     * 실패와 재선점을 5회 반복하면 FAILED 상태·시도 수 5·마지막 오류를 유지하는지 검증한다.
     */
    @Test
    void failsAfterMaximumAttempts() {
        OutboxEvent event = createClaimedEvent();

        for (int attempt = 1; attempt <= 5; attempt++) {
            event.fail(NOW.plusSeconds(attempt), 5, NOW.plusMinutes(attempt), "failure-" + attempt);
            if (attempt < 5) {
                event.claim(NOW.plusMinutes(attempt));
            }
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.getLastError()).isEqualTo("failure-5");
    }

    /**
     * 성공을 두 번 기록해도 SUCCEEDED 상태와 최초 처리 완료 시각이 유지되는지 검증한다.
     */
    @Test
    void succeededEventIgnoresDuplicateCompletion() {
        OutboxEvent event = createClaimedEvent();

        event.succeed(NOW);
        event.succeed(NOW.plusMinutes(1));

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(event.getProcessedAt()).isEqualTo(NOW);
    }

    /**
     * 선점 이벤트를 stale 복구하면 RETRY·시도 증가·재시도 시각을 반영하고 processingStartedAt을 제거하는지 검증한다.
     */
    @Test
    void recoversStaleProcessingEvent() {
        OutboxEvent event = createClaimedEvent();

        event.recover(NOW.plusMinutes(5), 5, NOW.plusMinutes(5).plusSeconds(10), "timeout");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5).plusSeconds(10));
        assertThat(event.getProcessingStartedAt()).isNull();
    }

    /**
     * 현재보다 1분 전에 생성된 이메일 인증 이벤트를 1초 전에 선점한 상태로 제공한다.
     */
    private OutboxEvent createClaimedEvent() {
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:1:123456",
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                "1",
                NOW.minusMinutes(1)
        );
        event.claim(NOW.minusSeconds(1));
        return event;
    }
}
