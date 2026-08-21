package com.typenull.pingdom.post.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingPeriod;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingResponse;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingScope;
import com.typenull.pingdom.place.application.service.place.PlaceRankingQueryService;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PostQueryController {

    private final PostQueryService postQueryService;
    private final PlaceRankingQueryService placeRankingQueryService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @GetMapping("/place-rankings")
    @Operation(summary = "장소 핫플·트렌드 랭킹 조회")
    public PlaceRankingResponse placeRankings(
            @RequestParam(defaultValue = "LOCAL") PlaceRankingScope scope,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "WEEK") PlaceRankingPeriod period,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return placeRankingQueryService.find(scope, latitude, longitude, radiusKm, period, category, page, limit, user == null ? null : user.userId());
    }

    @GetMapping("/posts")
    @Operation(
            summary = "게시글 목록 조회",
            description = "앱에서 게시글 목록을 최신순으로 페이지 단위 조회합니다. page는 1 이상, limit는 1~100 범위로 보정됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = PostListResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "posts": [
                                                {
                                                  "id": 10,
                                                  "title": "남강 야경",
                                                  "imageUrl": "https://example.com/images/post-10.jpg",
                                                  "description": "남강 산책 중 찍은 사진입니다.",
                                                  "userId": 3,
                                                  "username": "pingdom_user",
                                                  "createdAt": "2026-06-04T16:20:00",
                                                  "likeCount": 12,
                                                  "likedByMe": false,
                                                  "bookmarked": true,
                                                  "placeId": 5,
                                                  "placeName": "진주성"
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 125,
                                              "totalPages": 7,
                                              "hasNext": true
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            )
    })
    public PostListResponse listPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        Long userId = user.userId();
        return postQueryService.listPosts(page, limit, userId);
    }

    @GetMapping("/bookmarks")
    @Operation(
            summary = "저장한 게시글 목록 조회",
            description = "현재 인증된 사용자가 장소 기반으로 저장한 게시글을 북마크 최신순으로 조회합니다."
    )
    public PostListResponse listBookmarkedPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return postQueryService.listBookmarkedPosts(page, limit, user.userId());
    }

    @GetMapping({"/likes", "/like"})
    @Operation(
            summary = "좋아요한 게시글 목록 조회",
            description = "현재 인증된 사용자가 좋아요한 게시글을 최신 좋아요 순으로 페이지 단위 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요한 게시글 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PostListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            )
    })
    public PostListResponse listLikedPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user,
            HttpServletRequest request
    ) {
        if (request.getRequestURI().equals(request.getContextPath() + "/map/like")) {
            legacyApiUsageMetrics.record(LegacyApiEndpoint.MAP_LIKED_POSTS_GET);
        }
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return postQueryService.listLikedPosts(page, limit, user.userId());
    }

    @GetMapping("/posts/me")
    @Operation(
            summary = "내 게시글 목록 조회",
            description = "현재 인증된 사용자가 작성한 게시글만 검색 조건과 정렬 기준에 따라 페이지 단위 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 게시글 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PostListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            )
    })
    public PostListResponse listMyPosts(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "정렬 기준", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "게시글 검색 키워드. 게시글 ID, 제목, 연결 장소명, 설명으로 검색합니다.", example = "진주성")
            @RequestParam(required = false, defaultValue = "") String keyword,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return postQueryService.listMyPosts(page, limit, user.userId(), sortParam, keyword);
    }

    @GetMapping("/posts/{id}")
    @Operation(
            summary = "게시글 상세 조회",
            description = "앱에서 특정 게시글의 상세 정보와 연결된 장소 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = PostDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 10,
                                              "title": "남강 야경",
                                              "imageUrl": "https://example.com/images/post-10.jpg",
                                              "description": "남강 산책 중 찍은 사진입니다.",
                                              "userId": 3,
                                              "username": "pingdom_user",
                                              "createdAt": "2026-06-04T16:20:00",
                                              "likeCount": 12,
                                              "LikedByMe": false,
                                              "placeId": 5,
                                              "placeName": "진주성",
                                              "placeAddress": "경상남도 진주시 남강로 626",
                                              "latitude": 35.1894,
                                              "longitude": 128.0789
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 토큰",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "유효하지 않은 토큰입니다.",
                                              "code": "INVALID_TOKEN"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "게시글을 찾을 수 없습니다.",
                                              "code": "IMAGE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public PostDetailResponse getPost(
            @Parameter(description = "조회할 게시글 ID", example = "10") @PathVariable("id") Long postId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        Long userId = (user != null) ? user.userId() : null;
        return postQueryService.getPost(postId, userId);
    }
}
