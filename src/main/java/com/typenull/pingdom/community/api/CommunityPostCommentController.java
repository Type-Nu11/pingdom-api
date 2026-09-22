package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentListResponse;
import com.typenull.pingdom.community.application.CommunityPostCommentCommandService;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/community/posts/{postId}/comments")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_REACTION)
public class CommunityPostCommentController {

    private final CommunityPostCommentCommandService communityPostCommentCommandService;
    private final CommunityPostQueryService communityPostQueryService;

    @GetMapping
    @Operation(summary = "커뮤니티 게시글 댓글 목록 조회", description = "게시글 본문 아래에 표시할 댓글을 최신 댓글순으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음", useReturnTypeSchema = true)
    })
    public ResponseEntity<CommunityPostCommentListResponse> findAll(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(communityPostQueryService.findComments(postId, page, limit));
    }

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
