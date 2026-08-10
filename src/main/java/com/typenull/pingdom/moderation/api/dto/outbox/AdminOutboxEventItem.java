package com.typenull.pingdom.moderation.api.dto.outbox;

import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService.OutboxEventOperationSnapshot;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 Outbox 이벤트 운영 조회 항목")
public record AdminOutboxEventItem(
        @Schema(description = "이벤트 ID")
        String eventId,
        @Schema(description = "이벤트 유형", example = "EMAIL_VERIFICATION_REQUESTED")
        OutboxEventType eventType,
        @Schema(description = "aggregate 유형", example = "USER")
        String aggregateType,
        @Schema(description = "aggregate ID", example = "10")
        String aggregateId,
        @Schema(description = "처리 상태", example = "FAILED")
        OutboxEventStatus status,
        @Schema(description = "현재 재시도 주기의 실패 횟수", example = "5")
        int attemptCount,
        @Schema(description = "다음 처리 가능 시각")
        LocalDateTime nextAttemptAt,
        @Schema(description = "처리 선점 시각")
        LocalDateTime processingStartedAt,
        @Schema(description = "처리 완료 시각")
        LocalDateTime processedAt,
        @Schema(description = "마지막 실패 원인")
        String lastError,
        @Schema(description = "생성 시각")
        LocalDateTime createdAt,
        @Schema(description = "수정 시각")
        LocalDateTime updatedAt
) {
    public static AdminOutboxEventItem from(OutboxEvent event) {
        return new AdminOutboxEventItem(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getNextAttemptAt(),
                event.getProcessingStartedAt(),
                event.getProcessedAt(),
                event.getLastError(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public static AdminOutboxEventItem from(OutboxEventOperationSnapshot event) {
        return new AdminOutboxEventItem(
                event.eventId(),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId(),
                event.status(),
                event.attemptCount(),
                event.nextAttemptAt(),
                event.processingStartedAt(),
                event.processedAt(),
                event.lastError(),
                event.createdAt(),
                event.updatedAt()
        );
    }
}
