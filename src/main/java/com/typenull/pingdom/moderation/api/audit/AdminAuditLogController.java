package com.typenull.pingdom.moderation.api.audit;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.moderation.api.dto.audit.AdminAuditLogResponse;
import com.typenull.pingdom.moderation.application.query.audit.AdminAuditLogQueryService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
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
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
public class AdminAuditLogController {

    private final AdminAuditLogQueryService adminAuditLogQueryService;

    @GetMapping
    @Operation(
            summary = "관리자 감사 로그 조회",
            description = "관리자가 actor, action, target, 기간 조건으로 운영 감사 로그를 페이지 단위로 조회합니다."
    )
    public AdminAuditLogResponse listAuditLogs(
            @Parameter(description = "작업 관리자 ID", example = "1")
            @RequestParam(required = false) Long actorUserId,
            @Parameter(description = "작업 유형", example = "USER_BAN_APPLIED")
            @RequestParam(required = false) AdminAuditAction action,
            @Parameter(description = "대상 유형", example = "USER")
            @RequestParam(required = false) AdminAuditTargetType targetType,
            @Parameter(description = "대상 ID", example = "7")
            @RequestParam(required = false) String targetId,
            @Parameter(description = "조회 시작 시각", example = "2026-06-01T00:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-06-30T23:59:59")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser adminUser
    ) {
        return adminAuditLogQueryService.listAuditLogs(
                adminUser == null ? null : adminUser.userId(),
                actorUserId,
                action,
                targetType,
                targetId,
                from,
                to,
                page,
                limit
        );
    }
}
