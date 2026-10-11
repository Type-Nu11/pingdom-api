package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentListResponse;
import com.typenull.pingdom.community.application.CommunityPostCommentCommandService;
import com.typenull.pingdom.community.application.CommunityPostQueryService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}/comments")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_REACTION)
public class CommunityPostCommentController {

    private final CommunityPostCommentCommandService communityPostCommentCommandService;
    private final CommunityPostQueryService communityPostQueryService;

    @GetMapping
    @Operation(summary = "커뮤니티 게시글 댓글 목록 조회", description = "게시글 본문 아래에 표시할 댓글을 최신 댓글순으로 조회합니다. rootOnly=true는 최상위 댓글만 조회합니다. 숨김 부모의 대댓글은 제외합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityPostCommentListResponse> findAll(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "false") boolean rootOnly,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(communityPostQueryService.findComments(postId, page, limit, userId(user), rootOnly));
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "커뮤니티 대댓글 목록 조회", description = "공개 최상위 댓글의 대댓글을 최신순으로 조회합니다. totalCount는 해당 부모의 공개 대댓글 수입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대댓글 목록", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "대댓글을 부모로 조회할 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시글 또는 공개 부모 댓글 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommunityPostCommentListResponse> replies(@PathVariable long postId, @PathVariable long commentId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit, @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok(communityPostQueryService.findReplies(postId, commentId, page, limit, userId(user)));
    }

    private long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN)).userId();
    }

    @PostMapping
    @Operation(summary = "커뮤니티 게시글 댓글 등록", description = "parentCommentId를 생략하면 댓글, 지정하면 같은 게시글의 공개 최상위 댓글에 대댓글을 등록합니다. 대댓글의 답글은 지원하지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "댓글 등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "댓글 입력값이 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
