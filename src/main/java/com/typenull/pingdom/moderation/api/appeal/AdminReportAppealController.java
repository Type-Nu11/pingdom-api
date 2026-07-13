package com.typenull.pingdom.moderation.api.appeal;

import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealActionRequest;
import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealActionResponse;
import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealResponse;
import com.typenull.pingdom.moderation.application.service.appeal.ReportAppealService;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/report-appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminReportAppealController {

    private final ReportAppealService reportAppealService;

    @GetMapping
    @Operation(summary = "신고 이의제기 목록 조회")
    public AdminReportAppealResponse list(
            @Parameter(description = "이의제기 상태")
            @RequestParam(required = false) ReportAppealStatus status,
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return reportAppealService.list(status, page, limit);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "신고 이의제기 승인")
    public AdminReportAppealActionResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportAppealActionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        String reason = request == null ? null : request.reason();
        return reportAppealService.approve(id, reason, adminUserId);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "신고 이의제기 반려")
    public AdminReportAppealActionResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportAppealActionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        String reason = request == null ? null : request.reason();
        return reportAppealService.reject(id, reason, adminUserId);
    }
}
