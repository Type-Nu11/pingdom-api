package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityCommentLikeResponse;
import com.typenull.pingdom.community.application.CommunityCommentLikeService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/community/comments/{commentId}/likes")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_REACTION)
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "댓글 좋아요 수·본인 상태", useReturnTypeSchema = true),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "COMMENT_NOT_FOUND: 없는 댓글·숨김 댓글/부모/게시글",
        content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class)))
})
public class CommunityCommentLikeController {

    private final CommunityCommentLikeService communityCommentLikeService;

    @PostMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 등록") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityCommentLikeResponse> like(@PathVariable long commentId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityCommentLikeService.like(commentId, userId(user)));
    }

    @DeleteMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 취소") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityCommentLikeResponse> cancel(@PathVariable long commentId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityCommentLikeService.cancel(commentId, userId(user)));
    }

    @GetMapping
    @Operation(summary = "커뮤니티 댓글 좋아요 상태 조회") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityCommentLikeResponse> find(@PathVariable long commentId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityCommentLikeService.find(commentId, userId(user)));
    }

    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }
}
