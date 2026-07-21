package com.typenull.pingdom.moderation.api.dashboard;

import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentActivitiesResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
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
            description = "전체 장소, 게시글, 처리 대기 신고, 현재 밴 사용자 수와 최근 운영 변화 지표를 조회합니다. 오늘은 서버 기준 00시부터 현재까지, 최근 7일은 오늘을 포함한 7일 전 00시부터 현재까지 집계합니다."
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
                                      "bannedUserCount": 6,
                                      "operationalMetrics": {
                                        "today": {
                                          "period": "TODAY",
                                          "startedAt": "2026-07-21T00:00:00",
                                          "endedAt": "2026-07-21T15:30:00",
                                          "placeRegistrationCount": 3,
                                          "postRegistrationCount": 7
                                        },
                                        "last7Days": {
                                          "period": "LAST_7_DAYS",
                                          "startedAt": "2026-07-15T00:00:00",
                                          "endedAt": "2026-07-21T15:30:00",
                                          "placeRegistrationCount": 12,
                                          "postRegistrationCount": 31
                                        },
                                        "duplicatePlaceGroupCount": 2,
                                        "expiringBannedUserCount": 4,
                                        "missingLocationPlaceCount": 1,
                                        "expiringBanUntil": "2026-07-28T15:30:00",
                                        "collectedAt": "2026-07-21T15:30:00"
                                      }
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

    @GetMapping("/recent-activities")
    @Operation(
            summary = "관리자 대시보드 최근 운영 활동 조회",
            description = "최근 장소 등록, 게시글 등록, 신고 처리, 사용자 밴 및 밴 해제 내역을 조회합니다. limit 값은 내부적으로 1~50 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "최근 운영 활동 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminDashboardRecentActivitiesResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "places": [
                                        {
                                          "placeId": 10,
                                          "name": "핑덤 카페",
                                          "address": "서울특별시 중구 세종대로 110",
                                          "userId": 3,
                                          "registrant": "pingdom_user"
                                        }
                                      ],
                                      "posts": [
                                        {
                                          "postId": 22,
                                          "title": "야경이 좋은 장소",
                                          "userId": 3,
                                          "username": "pingdom_user",
                                          "placeId": 10,
                                          "placeName": "핑덤 카페",
                                          "createdAt": "2026-07-21T15:30:00"
                                        }
                                      ],
                                      "reports": [
                                        {
                                          "reportId": 30,
                                          "reportedImageId": 22,
                                          "title": "야경이 좋은 장소",
                                          "status": "ACCEPTED",
                                          "processedAt": "2026-07-21T16:00:00",
                                          "createdAt": "2026-07-21T15:40:00"
                                        }
                                      ],
                                      "userSanctions": [
                                        {
                                          "sanctionId": 40,
                                          "targetUserId": 5,
                                          "targetUsername": "reported_user",
                                          "action": "APPLIED",
                                          "banType": "PERMANENT",
                                          "reason": "부적절한 게시글입니다.",
                                          "processedAt": "2026-07-21T16:00:00"
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminDashboardRecentActivitiesResponse getRecentActivities(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return adminDashboardQueryService.getRecentActivities(limit);
    }

    @GetMapping("/pending-items")
    @Operation(
            summary = "관리자 대시보드 처리 필요 항목 조회",
            description = "우선 처리해야 하는 PENDING 신고 목록을 조회합니다. 각 항목은 유형, 대상 ID, 제목, 상태, 생성일을 제공합니다. limit 값은 내부적으로 1~50 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "처리 필요 항목 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminDashboardPendingItemsResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
                                          "type": "POST_REPORT",
                                          "targetId": 30,
                                          "title": "야경이 좋은 장소",
                                          "status": "PENDING",
                                          "createdAt": "2026-07-21T15:40:00"
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    public AdminDashboardPendingItemsResponse getPendingItems(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return adminDashboardQueryService.getPendingItems(limit);
    }
}
