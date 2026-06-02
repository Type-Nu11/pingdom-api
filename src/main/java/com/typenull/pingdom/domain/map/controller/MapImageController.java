package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.domain.map.dto.*;
import com.typenull.pingdom.domain.map.service.MapImageLikeService;
import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.map.service.PostReportService;
import com.typenull.pingdom.domain.map.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MapImageController {

    private final S3Service s3Service;
    private final PostReportService postReportService;
    private final MapImageLikeService mapImageLikeService;

    @PostMapping(value = "/post/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "게시글 업로드",
            description = "multipart/form-data로 카카오 장소 ID(권장) 또는 장소 ID(레거시), 제목, 부가 설명, 첨부 파일을 함께 업로드해 게시글로 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 업로드 성공",
                    content = @Content(schema = @Schema(implementation = MapImageResponse.class))
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
    public ResponseEntity<MapImageResponse> uploadPost(
            @Valid @ModelAttribute ImageUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return ResponseEntity.ok(s3Service.uploadImage(request,userId));
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
        s3Service.deleteImage(imageId,userId);
        return ResponseEntity.ok("게시글을 삭제했습니다.");
    }

    @PostMapping("/post/{id}/report")
    @Operation(
            summary = "게시글 신고",
            description = "지정한 게시글 ID의 게시글을 신고합니다. 동일 사용자는 같은 게시글을 한 번만 신고할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "게시글 신고 등록 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = "\"게시글 신고를 등록했습니다.\""
                            )
                    )
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
                                                "reason": "신고 사유는 필수입니다."
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 신고한 게시글",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "같은 게시글은 한 번만 신고할 수 있습니다.",
                                              "code": "ALREADY_REPORTED_IMAGE"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<String> report(
            @Parameter(description = "신고할 게시글 ID", example = "1") @PathVariable("id") Long imageId,
            @Valid @RequestBody PostReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        postReportService.report(imageId, user.userId(), user.username(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body("게시글 신고를 등록했습니다.");
    }

    @PostMapping("/like")
    public ResponseEntity<MapImageLikeResponse> like(
            @Valid @RequestBody MapImageLikeRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        MapImageLikeResponse response =
                mapImageLikeService.like(request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/like")
    public ResponseEntity<MapImageLikeResponse> likeClear(
            @Valid @RequestBody MapImageLikeRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        MapImageLikeResponse response =
                mapImageLikeService.notLike(request, userId);
        return ResponseEntity.ok(response);
    }
}
