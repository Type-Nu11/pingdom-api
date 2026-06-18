package com.typenull.pingdom.post.api;

import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.api.dto.image.PostUpdateResponse;
import com.typenull.pingdom.post.api.dto.image.PostUploadRequest;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;
import com.typenull.pingdom.post.application.query.PostQueryService;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PostController {

    private final S3Service s3Service;
    private final PostQueryService postQueryService;

    @GetMapping("/posts")
    @Operation(
            summary = "게시글 목록 조회",
            description = "앱에서 게시글 목록을 최신순으로 페이지 단위 조회합니다. page는 1 이상, limit는 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = PostListResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "posts": [
                                                {
                                                  "id": 10,
                                                  "title": "남강 야경",
                                                  "imageUrl": "https://example.com/images/post-10.jpg",
                                                  "description": "남강 산책 중 찍은 사진입니다.",
                                                  "userId": 3,
                                                  "username": "pingdom_user",
                                                  "createdAt": "2026-06-04T16:20:00",
                                                  "likeCount": 12,
                                                  "likedByMe": false,
                                                  "placeId": 5,
                                                  "placeName": "진주성"
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 125,
                                              "totalPages": 7,
                                              "hasNext": true
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
            )
    })
    public PostListResponse listPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return postQueryService.listPosts(page, limit, userId);
    }

    @GetMapping("/posts/{id}")
    @Operation(
            summary = "게시글 상세 조회",
            description = "앱에서 특정 게시글의 상세 정보와 연결된 장소 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = PostDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 10,
                                              "title": "남강 야경",
                                              "imageUrl": "https://example.com/images/post-10.jpg",
                                              "description": "남강 산책 중 찍은 사진입니다.",
                                              "userId": 3,
                                              "username": "pingdom_user",
                                              "createdAt": "2026-06-04T16:20:00",
                                              "likeCount": 12,
                                              "LikedByMe":false,
                                              "placeId": 5,
                                              "placeName": "진주성",
                                              "placeAddress": "경상남도 진주시 남강로 626",
                                              "latitude": 35.1894,
                                              "longitude": 128.0789
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "게시글을 찾을 수 없습니다.",
                                              "code": "IMAGE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public PostDetailResponse getPost(
            @Parameter(description = "조회할 게시글 ID", example = "10") @PathVariable("id") Long postId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return postQueryService.getPost(postId, userId);
    }

    @PostMapping(value = "/post/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "게시글 업로드",
            description = "multipart/form-data로 카카오 장소 ID(권장) 또는 장소 ID(레거시), 제목, 부가 설명, 첨부 파일을 함께 업로드해 게시글로 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 업로드 성공",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "kakaoPlaceId": "카카오 장소 ID는 50자 이하여야 합니다.",
                                                "title": "제목은 필수입니다.",
                                                "file": "파일은 필수입니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 포스트가 있음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "한 장소엔 하나의 포스트만 가능합니다.",
                                              "code": "ALREADY_POSTED"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "업로드 처리 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "업로드 과정에서 오류가 발생하였습니다.",
                                              "code": "UPLOAD_ERROR"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<PostResponse> uploadPost(
            @Valid @ModelAttribute PostUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return ResponseEntity.ok(s3Service.uploadImage(request, userId));
    }

    @PostMapping("/post/{id}/update")
    public ResponseEntity<PostUpdateResponse> updatePost(
            @Valid @ModelAttribute PostUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user,
            @Parameter(description = "수정할 게시글 ID", example = "1") @PathVariable("id") Long imageId
    ){
        Long userId = user.userId();
        return ResponseEntity.ok(s3Service.updateImage(request, userId, imageId));
    }

    @DeleteMapping("/post/{id}/delete")
    @Operation(
            summary = "게시글 삭제",
            description = "지정한 게시글 ID의 게시글을 삭제합니다. 본인 소유 게시글만 삭제할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 삭제 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"게시글을 삭제했습니다.\""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
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
                    description = "본인 소유가 아닌 게시글 삭제 시도",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "자신의 게시글만 삭제할 수 있습니다.",
                                              "code": "OTHERS_NOT_DELETED"
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
                                              "code": "IMAGE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> delete(
            @Parameter(description = "삭제할 게시글 ID", example = "1") @PathVariable("id") Long imageId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        s3Service.deleteImage(imageId, userId);
        return ResponseEntity.ok("게시글을 삭제했습니다.");
    }
}
