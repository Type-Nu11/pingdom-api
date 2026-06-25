package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.query.AdminPostQueryService;
import com.typenull.pingdom.post.infrastructure.storage.S3Service;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
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
    private final AdminPostQueryService adminPostQueryService;
    private final S3Service s3Service;

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
                                  "processedAt": null
                                }
                              ]
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
            ),
    })
    public AdminPostResponse listPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue =  "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "정렬 기준", example = "latest")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "게시글 검색 키워드. 게시글 ID, 제목, 작성자명, 작성자 ID, 연결 장소명, 설명으로 검색합니다.", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return adminPostQueryService.listPosts(page, limit, sortParam, keyword);
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
            description = "DB의 MapImage.s3Key 목록과 S3 map/ prefix 아래 객체 key를 비교해 DB에 없는 S3 key를 페이지 단위 삭제 후보로 반환합니다. 이 API는 실제 삭제를 수행하지 않습니다."
    )
    // 먼저 삭제 후보만 보여주고, 실제 삭제는 별도 확인 요청에서 처리한다.
    public S3Service.S3OrphanReport createS3OrphanReport(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return s3Service.createMapImageS3OrphanReport(page, limit);
    }

    @DeleteMapping("/posts/s3/orphans")
    @Operation(
            summary = "MapImage S3 고아 파일 삭제",
            description = "요청 본문으로 받은 key 목록만 삭제합니다. 삭제 후보를 다시 계산하지 않으며 null 또는 blank key는 무시합니다."
    )
    public S3Service.S3OrphanDeleteResult deleteS3Orphans(
            @RequestBody(required = false) AdminS3OrphanDeleteRequest request
    ) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "S3 삭제 리포트 확인이 필요합니다.");
        }
        return s3Service.deleteMapImageS3Keys(request.keys());
    }

    public record AdminS3OrphanDeleteRequest(List<String> keys, Boolean confirmed) {
    }
}
