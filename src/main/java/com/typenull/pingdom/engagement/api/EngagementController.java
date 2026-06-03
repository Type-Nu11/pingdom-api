package com.typenull.pingdom.engagement.api;

import com.typenull.pingdom.engagement.api.dto.MapImageLikeRequest;
import com.typenull.pingdom.engagement.api.dto.MapImageLikeResponse;
import com.typenull.pingdom.engagement.api.dto.PostReportRequest;
import com.typenull.pingdom.engagement.application.service.MapImageLikeService;
import com.typenull.pingdom.engagement.application.service.PostReportService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class EngagementController {

    private final PostReportService postReportService;
    private final MapImageLikeService mapImageLikeService;

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
        MapImageLikeResponse response = mapImageLikeService.like(request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/like/{imageId}")
    public ResponseEntity<MapImageLikeResponse> likeClear(
            @PathVariable("imageId") Long imageId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        MapImageLikeRequest request = new MapImageLikeRequest(imageId);
        MapImageLikeResponse response = mapImageLikeService.notLike(request, userId);
        return ResponseEntity.ok(response);
    }
}
