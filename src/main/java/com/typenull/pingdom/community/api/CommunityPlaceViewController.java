package com.typenull.pingdom.community.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.community.application.CommunityPlaceViewService;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/posts/{postId}/places/{placeId}/view")
@RequiredArgsConstructor
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.COMMUNITY_POST)
public class CommunityPlaceViewController {

    private final CommunityPlaceViewService communityPlaceViewService;

    @PostMapping
    @Operation(summary = "게시글 경유 장소 상세 조회", description = "연결 장소를 조회하고 사용자별 하루 1회만 커뮤니티 유입 조회수를 증가합니다.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PlaceDetailResponse> record(
            @PathVariable long postId,
            @PathVariable long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        long userId = JwtAuthenticatedUser.require(
                user,
                () -> new AuthException(AuthErrorCode.INVALID_TOKEN)
        ).userId();
        return ResponseEntity.ok(communityPlaceViewService.record(postId, placeId, userId));
    }
}
