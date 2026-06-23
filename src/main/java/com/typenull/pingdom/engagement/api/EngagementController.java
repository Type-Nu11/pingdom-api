package com.typenull.pingdom.engagement.api;

import com.typenull.pingdom.engagement.api.dto.like.MapImageLikeRequest;
import com.typenull.pingdom.engagement.api.dto.like.MapImageLikeResponse;
import com.typenull.pingdom.engagement.api.dto.report.PostReportRequest;
import com.typenull.pingdom.engagement.application.service.MapImageLikeResult;
import com.typenull.pingdom.engagement.application.service.MapImageLikeService;
import com.typenull.pingdom.engagement.application.service.PostReportService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitService;
import com.typenull.pingdom.shared.security.principal.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class EngagementController {

    private final PostReportService postReportService;
    private final MapImageLikeService mapImageLikeService;
    private final AbuseRateLimitService abuseRateLimitService;

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
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest servletRequest
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        abuseRateLimitService.checkPostReport(user.userId(), ClientIpResolver.resolve(servletRequest));
        postReportService.report(imageId, user.userId(), user.username(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body("게시글 신고를 등록했습니다.");
    }

    @PostMapping("/like")
    public ResponseEntity<MapImageLikeResponse> like(
            @Valid @RequestBody MapImageLikeRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest servletRequest
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        abuseRateLimitService.checkMapImageLike(user.userId(), ClientIpResolver.resolve(servletRequest));
        MapImageLikeResult result = mapImageLikeService.like(request.mapImageId(), user.userId());
        return ResponseEntity.ok(toResponse(result));
    }

    @DeleteMapping("/like/{postId}")
    public ResponseEntity<MapImageLikeResponse> likeClear(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest servletRequest
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        abuseRateLimitService.checkMapImageLike(user.userId(), ClientIpResolver.resolve(servletRequest));
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

    private MapImageLikeResponse toResponse(MapImageLikeResult result) {
        return new MapImageLikeResponse(result.userId(), result.mapImageId(), result.message());
    }
}
