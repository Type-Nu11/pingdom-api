package com.typenull.pingdom.moderation.api.community;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityCommentPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityCommentResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityPostPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityPostResponse;
import com.typenull.pingdom.moderation.application.service.community.AdminCommunityContentQueryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/community/posts")
@RequiredArgsConstructor
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.ADMIN)
@Tag(name = SwaggerTagCatalog.COMMUNITY_MANAGEMENT)
public class AdminCommunityContentController {

    private final AdminCommunityContentQueryService adminCommunityContentQueryService;

    @GetMapping
    @Operation(summary = "관리자 커뮤니티 글 목록 조회", description = "숨김 글을 포함해 카테고리와 숨김 상태로 조회합니다.")
    public AdminCommunityPostPageResponse findPosts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return adminCommunityContentQueryService.findPosts(categoryId, hidden, page, limit);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "관리자 커뮤니티 글 상세 조회", description = "숨김 여부와 연결 장소를 포함해 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "글 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "커뮤니티 글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityPostResponse findPost(@PathVariable Long postId) {
        return adminCommunityContentQueryService.findPost(postId);
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "관리자 커뮤니티 댓글 목록 조회", description = "숨김 댓글을 포함해 숨김 상태로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "커뮤니티 글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityCommentPageResponse findComments(
            @PathVariable Long postId,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return adminCommunityContentQueryService.findComments(postId, hidden, page, limit);
    }

    @GetMapping("/{postId}/comments/{commentId}")
    @Operation(summary = "관리자 커뮤니티 댓글 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "커뮤니티 글 또는 댓글을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminCommunityCommentResponse findComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return adminCommunityContentQueryService.findComment(postId, commentId);
    }
}
