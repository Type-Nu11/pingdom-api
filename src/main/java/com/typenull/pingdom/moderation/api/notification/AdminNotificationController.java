package com.typenull.pingdom.moderation.api.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadAllResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationUnreadCountResponse;
import com.typenull.pingdom.moderation.application.query.notification.AdminNotificationQueryService;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminNotificationController {

    private final AdminNotificationQueryService adminNotificationQueryService;

    @GetMapping
    @Operation(
            summary = "관리자 알림 목록 조회",
            description = "관리자가 사용자, 알림 유형, 읽음 상태, 기간 조건으로 인앱 알림을 페이지 단위로 조회합니다."
    )
    public AdminNotificationResponse listNotifications(
            @Parameter(description = "수신 사용자 ID", example = "10")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "알림 유형", example = "NEW_LIKE")
            @RequestParam(required = false) NotificationType type,
            @Parameter(description = "읽음 여부", example = "false")
            @RequestParam(required = false) Boolean read,
            @Parameter(description = "조회 시작 시각", example = "2026-07-01T00:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-31T23:59:59")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminNotificationQueryService.listNotifications(userId, type, read, from, to, page, limit);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "관리자 미확인 알림 개수 조회",
            description = "관리자가 아직 읽음 처리하지 않은 인앱 알림 개수를 조회합니다."
    )
    public AdminNotificationUnreadCountResponse countUnread() {
        return adminNotificationQueryService.countUnread();
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "관리자 알림 읽음 처리",
            description = "관리자가 운영 확인이 필요한 인앱 알림을 읽음 상태로 변경합니다."
    )
    public AdminNotificationReadResponse markAsRead(
            @Parameter(description = "알림 ID", example = "1")
            @PathVariable Long notificationId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        return adminNotificationQueryService.markAsRead(notificationId, adminUser.userId());
    }

    @PatchMapping("/read")
    @Operation(
            summary = "관리자 전체 알림 읽음 처리",
            description = "관리자가 모든 미확인 인앱 알림을 읽음 상태로 변경합니다."
    )
    public AdminNotificationReadAllResponse markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        return adminNotificationQueryService.markAllAsRead(adminUser.userId());
    }
}
