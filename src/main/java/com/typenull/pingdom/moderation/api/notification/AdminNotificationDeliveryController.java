package com.typenull.pingdom.moderation.api.notification;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationDeliveryResponse;
import com.typenull.pingdom.moderation.application.query.notification.AdminNotificationDeliveryQueryService;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notification-deliveries")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminNotificationDeliveryController {

    private final AdminNotificationDeliveryQueryService adminNotificationDeliveryQueryService;

    @GetMapping
    @Operation(
            summary = "관리자 알림 발송 결과 조회",
            description = "관리자가 사용자, 채널, 상태, 알림 유형, 기간 조건으로 알림 발송 결과를 페이지 단위로 조회합니다."
    )
    public AdminNotificationDeliveryResponse listDeliveries(
            @Parameter(description = "수신 사용자 ID", example = "10")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "발송 채널", example = "FCM")
            @RequestParam(required = false) NotificationDeliveryChannel channel,
            @Parameter(description = "발송 상태", example = "SUCCEEDED")
            @RequestParam(required = false) NotificationDeliveryStatus status,
            @Parameter(description = "인앱 알림 유형", example = "NEW_LIKE")
            @RequestParam(required = false) NotificationType notificationType,
            @Parameter(description = "Outbox 이벤트 유형", example = "EMAIL_VERIFICATION_REQUESTED")
            @RequestParam(required = false) OutboxEventType outboxEventType,
            @Parameter(description = "조회 시작 시각", example = "2026-06-01T00:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-06-30T23:59:59")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminNotificationDeliveryQueryService.listDeliveries(
                userId,
                channel,
                status,
                notificationType,
                outboxEventType,
                from,
                to,
                page,
                limit
        );
    }
}
