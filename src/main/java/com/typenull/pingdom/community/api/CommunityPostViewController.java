package com.typenull.pingdom.community.api;

import com.typenull.pingdom.community.api.dto.CommunityPostViewResponse;
import com.typenull.pingdom.community.application.CommunityPostViewService;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}/view")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class CommunityPostViewController {

    private final CommunityPostViewService communityPostViewService;

    @PostMapping
    @Operation(summary = "게시글 조회 기록", description = "공개 게시글의 조회수를 사용자·게시글·KST 날짜별 1회만 증가합니다. 상세 GET은 조회수를 변경하지 않습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "누적 게시글 조회수", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "공개 게시글 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CommunityPostViewResponse> record(
            @PathVariable long postId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        long userId = JwtAuthenticatedUser.require(
                user,
                () -> new AuthException(AuthErrorCode.INVALID_TOKEN)
        ).userId();
        return ResponseEntity.ok(communityPostViewService.record(postId, userId));
    }
}
