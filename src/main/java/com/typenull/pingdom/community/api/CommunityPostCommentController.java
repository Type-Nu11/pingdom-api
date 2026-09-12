package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateResponse;
import com.typenull.pingdom.community.application.CommunityPostCommentCommandService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class CommunityPostCommentController {

    private final CommunityPostCommentCommandService communityPostCommentCommandService;

    @PostMapping
    @Operation(summary = "커뮤니티 게시글 댓글 등록", description = "인증된 사용자가 존재하는 게시글에 댓글을 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "댓글 등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "댓글 입력값이 올바르지 않음", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음", useReturnTypeSchema = true)
    })
    public ResponseEntity<CommunityPostCommentCreateResponse> create(
            @PathVariable long postId,
            @Valid @RequestBody CommunityPostCommentCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticatedUser = JwtAuthenticatedUser.require(
                user,
                () -> new AuthException(AuthErrorCode.INVALID_TOKEN)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityPostCommentCommandService.create(postId, authenticatedUser.userId(), request));
    }
}
