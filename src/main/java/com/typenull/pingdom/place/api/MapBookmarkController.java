package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateResponse;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkRemoveResponse;
import com.typenull.pingdom.place.application.service.place.MapBookmarkService;
import com.typenull.pingdom.shared.security.principal.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MapBookmarkController {

    private final MapBookmarkService mapBookmarkService;

    @PostMapping("/bookmarks")
    @Operation(summary = "장소 북마크 추가", description = "placeId를 기반으로 장소를 북마크에 추가합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "북마크 추가 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "placeId": 17,
                                              "message": "장소 북마크를 추가했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소를 찾을 수 없습니다.",
                                              "code": "PLACE_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 북마크한 장소",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 북마크한 장소입니다.",
                                              "code": "BOOKMARK_ALREADY_EXISTS"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<BookmarkCreateResponse> createBookmark(
            @Valid @RequestBody BookmarkCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        BookmarkCreateResponse response = mapBookmarkService.createBookmark(request, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/bookmarks")
    @Operation(summary = "장소 북마크 해제", description = "placeId를 기반으로 장소 북마크를 해제합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "북마크 해제 성공",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                        {
                                          "userId": 1,
                                          "placeId": 17,
                                          "message": "장소 북마크를 해제했습니다."
                                        }
                                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "북마크를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                        {
                                          "message": "북마크를 찾을 수 없습니다.",
                                          "code": "BOOKMARK_NOT_FOUND"
                                        }
                                        """
                            )
                    )
            )
    })
    public ResponseEntity<BookmarkRemoveResponse> removeBookmark(
            @RequestParam Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ){
        BookmarkRemoveResponse response = mapBookmarkService.removeBookmark(placeId, user.userId());
        return ResponseEntity.ok().body(response);
    }
}
