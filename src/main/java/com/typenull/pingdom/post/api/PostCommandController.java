package com.typenull.pingdom.post.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.api.dto.image.PostUpdateResponse;
import com.typenull.pingdom.post.api.dto.image.PostUploadRequest;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.post.infrastructure.storage.S3Service;
import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
/** 게시글 업로드·수정·삭제 요청을 검증하고 이미지 처리 흐름으로 전달합니다. */
public class PostCommandController {

    private final S3Service s3Service;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "게시글 업로드",
            description = "multipart/form-data로 카카오 장소 ID(권장) 또는 장소 ID(레거시)를 사용해 기존 장소에 게시글을 저장합니다. 장소 참조 없이 좌표 토큰만 전달하는 자동 장소 생성은 승인된 등록 신청 경로가 제공될 때까지 차단됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 업로드 성공",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패 또는 이미 포스트가 있음",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "validation-failure",
                                            value = """
                                                    {
                                                      "message": "입력값을 확인해주세요.",
                                                      "errors": {
                                                        "placeName": "좌표 기반 업로드 시 장소 이름은 필수입니다.",
                                                        "title": "제목은 필수입니다.",
                                                        "file": "파일은 필수입니다."
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "already-posted",
                                            value = """
                                                    {
                                                      "message": "한 장소엔 하나의 포스트만 가능합니다.",
                                                      "code": "ALREADY_POSTED"
                                                    }
                                                    """
                                    )
                            }
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
                    description = "승인 없는 좌표 기반 장소 자동 생성 차단",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "승인된 장소 등록 신청을 통해서만 장소를 생성할 수 있습니다.",
                                              "code": "PLACE_REGISTRATION_APPROVAL_REQUIRED"
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
    @RateLimited(RateLimitAction.IMAGE_UPLOAD)
    /** 장소 참조 방식에 따라 게시글 업로드 또는 승인된 장소 등록 흐름을 선택합니다. */
    public ResponseEntity<PostResponse> uploadPost(
            @Valid @ModelAttribute PostUploadRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        recordCoordinatePlaceCreationAttempt(request);
        return uploadPostInternal(request, user);
    }

    @PostMapping("/posts/{id}")
    public ResponseEntity<PostUpdateResponse> updatePost(
            @Valid @ModelAttribute PostUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user,
            @Parameter(description = "수정할 게시글 ID", example = "1") @PathVariable("id") Long imageId
    ) {
        return updatePostInternal(request, user, imageId);
    }

    @DeleteMapping("/posts/{id}")
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
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return deleteInternal(imageId, user);
    }

    private ResponseEntity<PostResponse> uploadPostInternal(PostUploadRequest request, JwtAuthenticatedUser user) {
        Long userId = authenticatedUserId(user);
        return ResponseEntity.ok(s3Service.uploadImage(request, userId));
    }

    private void recordCoordinatePlaceCreationAttempt(PostUploadRequest request) {
        if (request.placeId() == null && !StringUtils.hasText(request.kakaoPlaceId())) {
            legacyApiUsageMetrics.record(LegacyApiEndpoint.POST_COORDINATE_PLACE_CREATE);
        }
    }

    private ResponseEntity<PostUpdateResponse> updatePostInternal(
            PostUpdateRequest request,
            JwtAuthenticatedUser user,
            Long imageId
    ) {
        Long userId = authenticatedUserId(user);
        return ResponseEntity.ok(s3Service.updateImage(request, userId, imageId));
    }

    private ResponseEntity<String> deleteInternal(Long imageId, JwtAuthenticatedUser user) {
        Long userId = authenticatedUserId(user);
        s3Service.deleteImage(imageId, userId);
        return ResponseEntity.ok("게시글을 삭제했습니다.");
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user.userId();
    }
}
