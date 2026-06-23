package com.typenull.pingdom.place.api;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class BookmarkQueryController {

    private final PlaceQueryService placeQueryService;

    @GetMapping("/bookmarks")
    @Operation(summary = "장소 북마크 목록 조회", description = "현재 인증된 사용자가 북마크한 장소 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "북마크 목록 조회 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "places": [
                                                {
                                                  "id": 17,
                                                  "name": "진주성",
                                                  "address": "경상남도 진주시 남강로 626",
                                                  "latitude": 35.1894,
                                                  "longitude": 128.0789
                                                }
                                              ],
                                              "page": 1,
                                              "limit": 20,
                                              "totalCount": 1,
                                              "totalPages": 1,
                                              "hasNext": false
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
    public ResponseEntity<PlaceListResponse> listBookmarks(
            @Parameter(description = "페이지 번호", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return ResponseEntity.ok(placeQueryService.listBookmarkedPlaces(user.userId(), page, limit));
    }
}
