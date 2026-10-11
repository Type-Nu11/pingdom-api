package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityCommentLikeResponse;
import com.typenull.pingdom.community.application.CommunityCommentLikeService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}/comments/{commentId}/likes")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_REACTION)
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "게시글·댓글이 없거나 숨김 처리됨. 숨김 부모의 대댓글 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class CommunityCommentLikeController {

    private final CommunityCommentLikeService communityCommentLikeService;

    @PostMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 등록")
    @ApiResponse(responseCode = "200", description = "댓글 좋아요 상태", useReturnTypeSchema = true)
    public ResponseEntity<CommunityCommentLikeResponse> like(
            @PathVariable long postId,
            @PathVariable long commentId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(communityCommentLikeService.like(postId, commentId, userId(user)));
    }

    @DeleteMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 취소")
    @ApiResponse(responseCode = "200", description = "댓글 좋아요 상태", useReturnTypeSchema = true)
    public ResponseEntity<CommunityCommentLikeResponse> cancel(
            @PathVariable long postId,
            @PathVariable long commentId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(communityCommentLikeService.cancel(postId, commentId, userId(user)));
    }

    @GetMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 상태 조회")
    @ApiResponse(responseCode = "200", description = "댓글 좋아요 상태", useReturnTypeSchema = true)
    public ResponseEntity<CommunityCommentLikeResponse> find(
            @PathVariable long postId,
            @PathVariable long commentId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(communityCommentLikeService.find(postId, commentId, userId(user)));
    }

    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }
}
