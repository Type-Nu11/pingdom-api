package com.typenull.pingdom.engagement.api;

import com.typenull.pingdom.engagement.api.dto.like.MapImageLikeRequest;
import com.typenull.pingdom.engagement.api.dto.like.MapImageLikeResponse;
import com.typenull.pingdom.engagement.api.dto.report.MyPostReportResponse;
import com.typenull.pingdom.engagement.api.dto.report.PostReportRequest;

import com.typenull.pingdom.engagement.application.query.PostReportQueryService;
import com.typenull.pingdom.engagement.application.service.MapImageLikeResult;
import com.typenull.pingdom.engagement.application.service.MapImageLikeService;
import com.typenull.pingdom.engagement.application.service.PostReportService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class EngagementController {

    private final PostReportService postReportService;
    private final PostReportQueryService postReportQueryService;
    private final MapImageLikeService mapImageLikeService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @PostMapping("/posts/{id}/report")
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
    @RateLimited(RateLimitAction.POST_REPORT)
    public ResponseEntity<String> report(
            @Parameter(description = "신고할 게시글 ID", example = "1") @PathVariable("id") Long imageId,
            @Valid @RequestBody PostReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.POST_REPORT);
        return reportInternal(imageId, request, user);
    }

    @Deprecated
    @PostMapping("/post/{id}/report")
    @Operation(
            summary = "게시글 신고(구 경로)",
            description = "기존 게시글 신고 경로입니다. `/map/posts/{id}/report` 사용을 권장합니다.",
            deprecated = true
    )
    @RateLimited(RateLimitAction.POST_REPORT)
    public ResponseEntity<String> reportLegacy(
            @Parameter(description = "신고할 게시글 ID", example = "1") @PathVariable("id") Long imageId,
            @Valid @RequestBody PostReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return reportInternal(imageId, request, user);
    }

    @GetMapping("/reports")
    @Operation(
            summary = "내 신고 내역 조회",
            description = "현재 인증된 사용자가 신고한 게시글 목록을 최신 신고순으로 페이지 단위 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 신고 내역 조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPostReportResponse.class))
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
    public ResponseEntity<MyPostReportResponse> listMyReports(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return ResponseEntity.ok(postReportQueryService.listMyReports(user.userId(), page, limit));
    }

    @PostMapping("/like")
    @RateLimited(RateLimitAction.MAP_IMAGE_LIKE)
    public ResponseEntity<MapImageLikeResponse> like(
            @Valid @RequestBody MapImageLikeRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        MapImageLikeResult result = mapImageLikeService.like(request.mapImageId(), user.userId());
        return ResponseEntity.ok(toResponse(result));
    }

    @DeleteMapping("/like/{postId}")
    @RateLimited(RateLimitAction.MAP_IMAGE_LIKE)
    public ResponseEntity<MapImageLikeResponse> likeClear(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        MapImageLikeResult result = mapImageLikeService.notLike(postId, user.userId());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/like/return/{postId}/{notificationsId}")
    public ResponseEntity<String> likeReturn(
            @PathVariable("postId") Long postId,
            @PathVariable("notificationsId") Long notificationsId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        mapImageLikeService.likeReturn(postId, notificationsId, user.userId());
        return ResponseEntity.ok().body("게시물 반환");
    }

    private ResponseEntity<String> reportInternal(
            Long imageId,
            PostReportRequest request,
            JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        postReportService.report(imageId, user.userId(), user.username(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body("게시글 신고를 등록했습니다.");
    }

    private MapImageLikeResponse toResponse(MapImageLikeResult result) {
        return new MapImageLikeResponse(result.userId(), result.mapImageId(), result.message());
    }
}
