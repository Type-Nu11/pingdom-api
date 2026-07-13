package com.typenull.pingdom.moderation.api.report;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersItem;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersResponse;
import com.typenull.pingdom.moderation.application.AdminReportService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @PostMapping("/{id}/accept")
    @Operation(
            summary = "신고 수락 처리",
            description = "관리자가 신고를 수락하고 대상 사용자를 제재하며 신고 대상 게시글을 숨김 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 수락 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "reportId": 1,
                                              "status": "ACCEPTED",
                                              "reportedUserId": 5,
                                              "banned": true,
                                              "processedAt": "2026-05-07T21:00:00"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신고 내역 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "신고 내역을 찾을 수 없습니다.",
                                              "code": "REPORT_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 신고",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 처리된 신고입니다.",
                                              "code": "REPORT_ALREADY_PROCESSED"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminReportActionResponse acceptReport(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        return adminReportService.acceptReport(id, adminUser.userId());
    }

    @PostMapping("/{id}/decline")
    @Operation(
            summary = "신고 거절 처리",
            description = "관리자가 신고를 거절 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 거절 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "reportId": 1,
                                              "status": "DECLINED",
                                              "reportedUserId": 5,
                                              "banned": false,
                                              "processedAt": "2026-05-07T21:00:00"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신고 내역 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "신고 내역을 찾을 수 없습니다.",
                                              "code": "REPORT_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 처리된 신고",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 처리된 신고입니다.",
                                              "code": "REPORT_ALREADY_PROCESSED"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminReportActionResponse declineReport(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return adminReportService.declineReport(id, adminUserId);
    }

    @GetMapping("/reported-users")
    @Operation(
            summary = "관리자 신고 유저 목록 조회",
            description = "관리자가 PENDING 상태의 신고 유저 목록을 조회합니다. limit 값은 내부적으로 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 유저 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = ReportedUsersResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "users": [
                                        {
                                          "reportId": 12,
                                          "reporterUserId": 3,
                                          "reporterUsername": "reporter01",
                                          "reportedImageId": 101,
                                          "reportedUserId": 55,
                                          "reason": "욕설"
                                        }
                                      ],
                                      "page": 1,
                                      "limit": 20,
                                      "totalCount": 123,
                                      "totalPages": 7,
                                      "hasNext": true
                                    }
                                    """)
                    )
            )
    })
    public ReportedUsersResponse getReportedUsers(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "검색 키워드. 신고자 이름, 신고 사유, 신고받은 유저 ID로 검색합니다.", example = "욕설")
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return adminReportService.getReportedUsers(page, limit, keyword);
    }

    @GetMapping("/reported-users/{id}")
    @Operation(
            summary = "관리자 신고 유저 상세 조회",
            description = "관리자가 신고 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 유저 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = ReportedUsersItem.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "reportId": 12,
                                      "reporterUserId": 3,
                                      "reporterUsername": "reporter01",
                                      "reportedImageId": 101,
                                      "reportedUserId": 55,
                                      "reason": "욕설"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "신고를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "신고를 찾을 수 없습니다.",
                                      "code": "REPORT_NOT_FOUND"
                                    }
                                    """)
                    )
            )
    })
    public ReportedUsersItem getReportedUser(
            @Parameter(description = "조회할 신고 ID", example = "12")
            @PathVariable("id") Long id
    ) {
        return adminReportService.getReportedUser(id);
    }
}
