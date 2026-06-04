package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.query.AdminPostQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPostController {

    private final AdminPostService adminPostService;
    private final AdminPostQueryService adminPostQueryService;

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
                              "thumbnailUrl": "https://example.com/thumb.jpg",
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
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue =  "1") int page,
            @Parameter(description = "정렬 기준", example = "latest")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam
    ) {
        return adminPostQueryService.listPosts(limit, page, sortParam);
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
                          "thumbnailUrl": "https://example.com/thumb.jpg",
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
            description = "관리자가 게시글을 강제로 삭제합니다."
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
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "게시글 삭제 처리 실패",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "delete-failed",
                                            value = """
                                                    {
                                                      "message": "게시글 삭제에 실패했습니다.",
                                                      "code": "POST_DELETE_FAILED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "s3-connection-error",
                                            value = """
                                                    {
                                                      "message": "S3 연결에 실패했습니다.",
                                                      "code": "S3_CONNECTION_ERROR"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "삭제할 게시글 ID", example = "10") @PathVariable("id") Long id
    ) {
        adminPostService.deletePost(id);
        return ResponseEntity.noContent().build();
    }
}