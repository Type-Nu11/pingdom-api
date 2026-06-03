package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.moderation.application.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @PostMapping("/{id}/accept")
    @Operation(
            summary = "신고 수락 처리",
            description = "관리자가 신고를 수락하고 대상 사용자를 제재하며 신고 대상 게시글을 삭제합니다."
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
    public AdminReportActionResponse acceptReport(@PathVariable Long id) {
        return adminReportService.acceptReport(id);
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
    public AdminReportActionResponse declineReport(@PathVariable Long id) {
        return adminReportService.declineReport(id);
    }
}
