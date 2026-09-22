package com.typenull.pingdom.moderation.api.community;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.domain.CommunityReportTargetType;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportActionResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportResponse;
import com.typenull.pingdom.moderation.application.service.community.AdminCommunityReportService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/community-reports")
@RequiredArgsConstructor
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.ADMIN)
@Tag(name = SwaggerTagCatalog.REPORT_APPEAL)
public class AdminCommunityReportController {

    private final AdminCommunityReportService adminCommunityReportService;

    @GetMapping
    @Operation(summary = "관리자 커뮤니티 신고 목록 조회")
    public AdminCommunityReportPageResponse list(
            @RequestParam(required = false) CommunityReportStatus status,
            @RequestParam(required = false) CommunityReportTargetType targetType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return adminCommunityReportService.list(status, targetType, page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "관리자 커뮤니티 신고 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminCommunityReportResponse.class),
                            examples = {
                                    @ExampleObject(name = "POST", summary = "글 신고: postId와 targetId가 동일",
                                            value = """
                                                    {"reportId":1,"targetType":"POST","targetId":101,"postId":101,
                                                     "targetHidden":false,"reporterUserId":10,"reason":"SPAM",
                                                     "description":"글 신고","status":"PENDING","createdAt":"2026-09-17T10:00:00",
                                                     "processedByAdminUserId":null,"processedAt":null}
                                                    """),
                                    @ExampleObject(name = "COMMENT", summary = "댓글 신고: postId는 원문 글, targetId는 댓글",
                                            value = """
                                                    {"reportId":2,"targetType":"COMMENT","targetId":202,"postId":101,
                                                     "targetHidden":false,"reporterUserId":10,"reason":"ABUSE",
                                                     "description":"댓글 신고","status":"PENDING","createdAt":"2026-09-17T10:00:00",
                                                     "processedByAdminUserId":null,"processedAt":null}
                                                    """)
                            })),
            @ApiResponse(responseCode = "404", description = "커뮤니티 신고를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityReportResponse get(@PathVariable Long reportId) {
        return adminCommunityReportService.get(reportId);
    }

    @PostMapping("/{reportId}/accept")
    @Operation(summary = "관리자 커뮤니티 신고 수락", description = "수락한 신고 대상 글 또는 댓글을 숨깁니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 수락 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "커뮤니티 신고 또는 대상 콘텐츠를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 처리된 신고",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityReportActionResponse accept(
            @PathVariable Long reportId,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminCommunityReportService.accept(reportId, admin.userId());
    }

    @PostMapping("/{reportId}/decline")
    @Operation(summary = "관리자 커뮤니티 신고 반려", description = "반려는 신고 상태만 변경하고 대상 노출 상태를 바꾸지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 반려 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "커뮤니티 신고를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 처리된 신고",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityReportActionResponse decline(
            @PathVariable Long reportId,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminCommunityReportService.decline(reportId, admin.userId());
    }
}
