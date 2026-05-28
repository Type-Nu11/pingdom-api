package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import com.typenull.pingdom.domain.admin.service.AdminReportQueryService;
import com.typenull.pingdom.domain.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminReportController {

    private final AdminReportQueryService adminReportQueryService;
    private final AdminReportService adminReportService;

    @GetMapping
    @Operation(
            summary = "신고 목록 조회",
            description = "관리자가 신고 목록을 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 목록 조회 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "content": [
                                                {
                                                  "reportId": 1,
                                                  "imageId": 10,
                                                  "reporterUsername": "reporter01",
                                                  "reason": "부적절한 게시글입니다.",
                                                  "status": "PENDING",
                                                  "processedAt": null
                                                }
                                              ],
                                              "totalElements": 1,
                                              "totalPages": 1,
                                              "number": 0,
                                              "size": 20
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
            )
    })
    public Page<AdminReportSummaryResponse> listReports(
            @Parameter(description = "조회할 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminReportQueryService.listReports(page, limit);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "신고 상세 조회",
            description = "관리자가 특정 신고 내역을 상세 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "신고 상세 조회 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "reportId": 1,
                                              "imageId": 10,
                                              "reportedUserId": 5,
                                              "imageUrl": "https://example.com/image.jpg",
                                              "reporterUserId": 7,
                                              "reporterUsername": "reporter01",
                                              "reason": "부적절한 게시글입니다.",
                                              "status": "PENDING",
                                              "processedAt": null
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
            )
    })
    public AdminReportDetailResponse getReport(@PathVariable Long id) {
        return adminReportQueryService.getReport(id);
    }

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
