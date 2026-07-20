package com.typenull.pingdom.moderation.api.dashboard;

import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.query.dashboard.AdminDashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminDashboardController {

    private final AdminDashboardQueryService adminDashboardQueryService;

    @GetMapping("/summary")
    @Operation(
            summary = "관리자 대시보드 요약 조회",
            description = "전체 장소, 게시글, 처리 대기 신고, 현재 밴 사용자 수를 조회합니다. 각 항목은 개별 집계 시점의 운영 현황을 나타냅니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "대시보드 요약 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminDashboardSummaryResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "placeCount": 44,
                                      "postCount": 58,
                                      "pendingReportCount": 5,
                                      "bannedUserCount": 6
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminDashboardSummaryResponse getSummary() {
        return adminDashboardQueryService.getSummary();
    }
}
