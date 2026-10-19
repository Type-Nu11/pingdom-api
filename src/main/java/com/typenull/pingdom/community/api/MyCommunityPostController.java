package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.MyCommunityPostListResponse;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class MyCommunityPostController {
    private final CommunityPostQueryService service;

    @GetMapping("/users/me/community/posts")
    @Operation(summary = "내 커뮤니티 게시글 목록", description = "JWT 사용자 본인의 글만 createdAt DESC, postId DESC 순으로 조회합니다. 숨김 글은 hidden=true로 포함하고 삭제 글은 제외합니다. 숨김 글은 공개 상세·수정 불가, 삭제 가능합니다. 마이페이지 미리보기는 limit=2를 사용합니다. page는 1부터, limit은 1~100이며 기본 20입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카드와 totalElements/totalPages/hasNext 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "페이지 범위 오류", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "JWT 없음 또는 무효", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MyCommunityPostListResponse findOwn(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        JwtAuthenticatedUser authenticated = JwtAuthenticatedUser.require(user, () -> new AuthException(AuthErrorCode.INVALID_TOKEN));
        return service.findOwnPosts(authenticated.userId(), page, limit);
    }
}
