package com.typenull.pingdom.moderation.api.post;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.moderation.api.dto.report.AdminPostReportBulkActionResponse;
import com.typenull.pingdom.moderation.domain.AdminPostReviewStatus;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.AdminReportService;
import com.typenull.pingdom.moderation.application.query.post.AdminPostQueryService;
import com.typenull.pingdom.post.infrastructure.storage.MapImageS3OrphanReportService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPostController {

    private final AdminPostService adminPostService;
    private final AdminReportService adminReportService;
    private final AdminPostQueryService adminPostQueryService;
    private final MapImageS3OrphanReportService mapImageS3OrphanReportService;

    @GetMapping("/posts")
    @Operation(
            summary = "관리자 게시글 목록 조회",
            description = "관리자가 최근 게시글 목록을 조회합니다. 각 게시글에는 연결된 신고 목록이 함께 포함됩니다. limit 값은 내부적으로 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminPostResponse.class),
                            examples = @ExampleObject(value = """
                        {
                          "posts": [
                            {
                              "id": 1,
                              "imageUrl": "https://example.com/original.jpg",
                              "userId": 1,
                              "username": "pingdom_user",
                              "createdAt": "2026-05-21T11:37:53.336Z",
                              "reports": [
                                {
                                  "reportId": 10,
                                  "reporterUserId": 3,
                                  "reporterUsername": "reporter01",
                                  "reason": "부적절한 게시글입니다.",
                                  "status": "PENDING",
                                  "createdAt": "2026-05-21T11:50:00",
                                  "processedAt": null
                                }
                              ]
                            }
                          ],
                          "page": 1,
                          "limit": 20,
                          "totalCount": 123,
                          "totalPages": 7,
                          "hasNext": true,
                          "counts": {
                            "all": 123,
                            "pending": 11,
                            "processed": 43,
                            "normal": 69
                          }
                        }
                        """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "reportStatus와 reviewStatus 동시 사용 불가"),
    })
    public AdminPostResponse listPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue =  "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "정렬 기준", example = "latest")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "게시글 검색 키워드. 게시글 ID, 제목, 작성자명, 작성자 ID, 연결 장소명, 설명으로 검색합니다.", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword,
            @Parameter(description = "게시글 단위 검수 상태 필터. ALL, PENDING, PROCESSED, NORMAL", example = "PENDING")
            @RequestParam(defaultValue = "ALL") AdminPostReviewStatus reviewStatus,
            @Parameter(description = "신고 상태 필터. 예: PENDING", example = "PENDING")
            @RequestParam(required = false) PostReportStatus reportStatus
    ) {
        validateExclusiveReportFilters(reportStatus, reviewStatus);
        return adminPostQueryService.listPosts(page, limit, sortParam, keyword, reviewStatus, reportStatus);
    }

    private void validateExclusiveReportFilters(PostReportStatus reportStatus, AdminPostReviewStatus reviewStatus) {
        if (reportStatus != null && reviewStatus != AdminPostReviewStatus.ALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportStatus와 reviewStatus는 동시에 사용할 수 없습니다.");
        }
    }

    @GetMapping("/posts/{id}")
    @Operation(
            summary = "관리자 게시글 상세 조회",
            description = "관리자가 게시글 상세 정보와 연결된 신고 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminPostItem.class),
                            examples = @ExampleObject(value = """
                        {
                          "id": 1,
                          "name": "신고 대상 제목",
                          "imageUrl": "https://example.com/original.jpg",
                          "userId": 1,
                          "username": "pingdom_user",
                          "createdAt": "2026-05-21T11:37:53.336Z",
                          "description": "신고 대상 설명",
                          "likeCount": 10,
                          "placeName": "대구소프트웨어마이스터고등학교",
                          "reports": [
                            {
                              "reportId": 10,
                              "reporterUserId": 3,
                              "reporterUsername": "reporter01",
                              "reason": "부적절한 게시글입니다.",
                              "status": "PENDING",
                              "createdAt": "2026-05-21T11:50:00",
                              "processedAt": null
                            }
                          ]
                        }
                        """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "게시글을 찾을 수 없습니다.",
                                              "code": "POST_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminPostItem getPost(
            @Parameter(description = "조회할 게시글 ID", example = "10") @PathVariable("id") Long id
    ) {
        return adminPostQueryService.getPost(id);
    }

    @PostMapping("/posts/{postId}/reports/accept")
    @Operation(
            summary = "게시글 단위 신고 일괄 수락",
            description = "관리자가 게시글에 연결된 PENDING 신고를 모두 수락하고 게시글 숨김 및 대상 사용자 제재를 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 신고 일괄 수락 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPostReportBulkActionResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "postId": 10,
                                      "status": "ACCEPTED",
                                      "processedReportCount": 3,
                                      "visibilityStatus": "AUTO_HIDDEN",
                                      "hiddenAt": "2026-05-21T10:15:30",
                                      "hiddenReason": "REPORT_BULK_ACCEPTED",
                                      "processedAt": "2026-05-21T10:15:30"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "처리할 PENDING 신고 없음")
    })
    public AdminPostReportBulkActionResponse acceptPostReports(
            @Parameter(description = "신고를 일괄 수락할 게시글 ID", example = "10")
            @PathVariable Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return adminReportService.acceptPostReports(postId, adminUserId);
    }

    @PostMapping("/posts/{postId}/reports/decline")
    @Operation(
            summary = "게시글 단위 신고 일괄 거절",
            description = "관리자가 게시글에 연결된 PENDING 신고를 모두 거절합니다. 게시글 공개 상태와 대상 사용자 제재 상태는 변경하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 신고 일괄 거절 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPostReportBulkActionResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "postId": 10,
                                      "status": "DECLINED",
                                      "processedReportCount": 3,
                                      "visibilityStatus": "ACTIVE",
                                      "hiddenAt": null,
                                      "hiddenReason": null,
                                      "processedAt": "2026-05-21T10:15:30"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "처리할 PENDING 신고 없음")
    })
    public AdminPostReportBulkActionResponse declinePostReports(
            @Parameter(description = "신고를 일괄 거절할 게시글 ID", example = "10")
            @PathVariable Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return adminReportService.declinePostReports(postId, adminUserId);
    }

    @DeleteMapping("/posts/{id}/delete")
    @Operation(
            summary = "관리자 게시글 삭제",
            description = "관리자가 게시글을 강제로 삭제합니다. S3 객체 삭제는 DB 삭제 확정 후 Outbox로 비동기 처리됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "게시글 삭제 성공"
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
                    description = "게시글을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "게시글을 찾을 수 없습니다.",
                                              "code": "POST_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "삭제할 게시글 ID", example = "10") @PathVariable("id") Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        adminPostService.deletePost(id, adminUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/s3/orphans/report")
    @Operation(
            summary = "MapImage S3 고아 파일 삭제 후보 리포트 생성",
            description = "최근 생성된 S3 고아 파일 리포트의 삭제 후보를 페이지 단위로 조회합니다. 이 API는 S3/DB 전체 스캔이나 실제 삭제를 수행하지 않습니다."
    )
    public MapImageS3OrphanReportService.S3OrphanReport createS3OrphanReport(
            @Parameter(description = "조회할 리포트 ID. 생략하면 최근 생성된 리포트를 조회합니다.")
            @RequestParam(required = false) String reportId,
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return mapImageS3OrphanReportService.getMapImageS3OrphanReport(reportId, page, limit);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/posts/s3/orphans/report/refresh")
    @Operation(
            summary = "MapImage S3 고아 파일 리포트 생성 시작",
            description = "DB와 S3를 백그라운드에서 비교해 Redis에 리포트 결과를 저장합니다. 생성된 리포트는 GET report API로 페이지 조회합니다."
    )
    public MapImageS3OrphanReportService.S3OrphanReportStatus refreshS3OrphanReport() {
        return mapImageS3OrphanReportService.refreshMapImageS3OrphanReport();
    }

    @GetMapping("/posts/s3/orphans/report/status")
    @Operation(
            summary = "MapImage S3 고아 파일 리포트 생성 상태 조회",
            description = "리포트 생성 작업의 RUNNING/COMPLETED/FAILED 상태와 집계 정보를 조회합니다."
    )
    public MapImageS3OrphanReportService.S3OrphanReportStatus getS3OrphanReportStatus(
            @Parameter(description = "조회할 리포트 ID. 생략하면 최근 생성된 리포트를 조회합니다.")
            @RequestParam(required = false) String reportId
    ) {
        try {
            return mapImageS3OrphanReportService.getMapImageS3OrphanReportStatus(reportId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @DeleteMapping("/posts/s3/orphans")
    @Operation(
            summary = "MapImage S3 고아 파일 삭제",
            description = "완료된 리포트의 삭제 후보인지 확인하고, 삭제 직전 DB에서 사용 중인 키를 다시 제외한 뒤 S3 객체를 삭제합니다."
    )
    public MapImageS3OrphanReportService.S3OrphanDeleteResult deleteS3Orphans(
            @Valid @RequestBody(required = false) AdminS3OrphanDeleteRequest request
    ) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "S3 삭제 리포트 확인이 필요합니다.");
        }
        try {
            return mapImageS3OrphanReportService.deleteMapImageS3Candidates(request.reportId(), request.keys());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    public record AdminS3OrphanDeleteRequest(
            @NotBlank(message = "S3 고아 파일 리포트 ID는 필수입니다.") String reportId,
            @Size(max = 100, message = "S3 삭제 요청은 최대 100개까지 가능합니다.") List<String> keys,
            Boolean confirmed
    ) {
    }
}
