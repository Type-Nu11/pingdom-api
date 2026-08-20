package com.typenull.pingdom.moderation.api.notification;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadAllResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationReadResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationUnreadCountResponse;
import com.typenull.pingdom.moderation.application.query.notification.AdminNotificationQueryService;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCommandService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminNotificationController {

    private final AdminNotificationQueryService adminNotificationQueryService;
    private final AdminNotificationCommandService adminNotificationCommandService;

    @GetMapping
    @Operation(
            summary = "관리자 알림 목록 조회",
            description = "인증된 관리자가 자신에게 전달된 운영 알림을 유형, 읽음 상태, 기간 조건으로 조회합니다. "
                    + "알림 token은 report:{reportId}, place:{placeId}, sanction:{sanctionId} 형식을 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 알림 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminNotificationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "notifications": [
                                        {
                                          "notificationId": 1,
                                          "userId": 10,
                                          "type": "ADMIN_REPORT_RECEIVED",
                                          "title": "신고 접수 알림",
                                          "body": "신고 ID 30 접수가 게시글 ID 12에 등록되었습니다.",
                                          "token": "report:30",
                                          "read": false,
                                          "createdAt": "2026-07-21T15:30:00"
                                        }
                                      ],
                                      "page": 1,
                                      "limit": 20,
                                      "totalCount": 1,
                                      "totalPages": 1,
                                      "hasNext": false
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 기간 오류",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "알림 조회 종료 시각은 시작 시각보다 이후여야 합니다.",
                                      "code": "INVALID_NOTIFICATION_FILTER_PERIOD"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminNotificationResponse listNotifications(
            @Parameter(
                    description = "하위 호환용 수신 관리자 ID. 생략을 권장하며 인증 관리자 ID와 같아야 합니다.",
                    example = "10",
                    deprecated = true
            )
            @RequestParam(required = false) Long userId,
            @Parameter(
                    description = "관리자 알림 유형: ADMIN_REPORT_RECEIVED, ADMIN_REPORT_PROCESSED, "
                            + "ADMIN_DUPLICATE_PLACE_DETECTED, ADMIN_USER_SANCTION",
                    example = "ADMIN_REPORT_RECEIVED"
            )
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
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        if (userId != null && !userId.equals(adminUser.userId())) {
            throw new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED);
        }
        return adminNotificationQueryService.listNotifications(
                adminUser.userId(),
                type,
                read,
                from,
                to,
                page,
                limit
        );
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "관리자 미확인 알림 개수 조회",
            description = "관리자가 아직 읽음 처리하지 않은 인앱 알림 개수를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "미확인 알림 개수 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminNotificationUnreadCountResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "unreadCount": 3
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminNotificationUnreadCountResponse countUnread(
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminNotificationQueryService.countUnread(adminUser.userId());
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "관리자 알림 읽음 처리",
            description = "관리자가 운영 확인이 필요한 인앱 알림을 읽음 상태로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "알림 읽음 처리 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminNotificationReadResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "notificationId": 1,
                                      "read": true,
                                      "message": "알림을 읽음 처리했습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "알림을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "알림을 찾을 수 없습니다.",
                                      "code": "NOTIFICATION_NOT_FOUND"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminNotificationReadResponse markAsRead(
            @Parameter(description = "알림 ID", example = "1")
            @PathVariable Long notificationId,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminNotificationCommandService.markAsRead(notificationId, adminUser.userId());
    }

    @PatchMapping("/read")
    @Operation(
            summary = "관리자 전체 알림 읽음 처리",
            description = "관리자가 모든 미확인 인앱 알림을 읽음 상태로 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "전체 알림 읽음 처리 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminNotificationReadAllResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "updatedCount": 3,
                                      "message": "전체 알림을 읽음 처리했습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminNotificationReadAllResponse markAllAsRead(
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminNotificationCommandService.markAllAsRead(adminUser.userId());
    }
}
