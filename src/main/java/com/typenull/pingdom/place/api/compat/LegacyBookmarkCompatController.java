package com.typenull.pingdom.place.api.compat;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateResponse;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkRemoveResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.place.application.service.place.MapBookmarkService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class LegacyBookmarkCompatController {

    private final PlaceQueryService placeQueryService;
    private final MapBookmarkService mapBookmarkService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @Deprecated
    @GetMapping("/users/bookmarks")
    public ResponseEntity<PlaceListResponse> listBookmarks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.BOOKMARK_LIST);
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return ResponseEntity.ok(placeQueryService.listBookmarkedPlaces(user.userId(), page, limit));
    }

    @Deprecated
    @PostMapping("/map/bookmarks")
    public ResponseEntity<BookmarkCreateResponse> createBookmark(
            @Valid @RequestBody BookmarkCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.BOOKMARK_CREATE);
        BookmarkCreateResponse response = mapBookmarkService.createBookmark(request, authenticatedUserId(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @DeleteMapping("/map/bookmarks")
    public ResponseEntity<BookmarkRemoveResponse> removeBookmark(
            @RequestParam Long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.BOOKMARK_DELETE);
        BookmarkRemoveResponse response = mapBookmarkService.removeBookmark(placeId, authenticatedUserId(user));
        return ResponseEntity.ok(response);
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user.userId();
    }
}
