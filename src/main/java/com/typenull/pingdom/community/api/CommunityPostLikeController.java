package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityPostLikeResponse;
import com.typenull.pingdom.community.application.CommunityPostLikeService;
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
@RequestMapping("/community/posts/{postId}/likes")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_REACTION)
public class CommunityPostLikeController {

    private final CommunityPostLikeService communityPostLikeService;

    @PostMapping
    @Operation(summary = "커뮤니티 게시글 좋아요 등록") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityPostLikeResponse> like(@PathVariable long postId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityPostLikeService.like(postId, userId(user)));
    }

    @DeleteMapping
    @Operation(summary = "커뮤니티 게시글 좋아요 취소") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityPostLikeResponse> cancel(@PathVariable long postId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityPostLikeService.cancel(postId, userId(user)));
    }

    @GetMapping
    @Operation(summary = "커뮤니티 게시글 좋아요 상태 조회") @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommunityPostLikeResponse> find(@PathVariable long postId, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityPostLikeService.find(postId, userId(user)));
    }

    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }
}
