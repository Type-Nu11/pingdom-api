package com.typenull.pingdom.place.api.compat;

import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateRequest;
import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateResponse;
import com.typenull.pingdom.place.api.dto.place.create.PlaceCreateResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.upload.PlaceUploadRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceSearchCondition;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.explanation.PlaceRecommendationExplanationQueryService;
import com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryService;
import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class LegacyPlaceCompatController {

    private final PlaceQueryService placeQueryService;
    private final MapPlaceService mapPlaceService;
    private final PlaceRecommendationQueryService placeRecommendationQueryService;
    private final PlaceRecommendationClickService placeRecommendationClickService;
    private final PlaceRecommendationExplanationQueryService placeRecommendationExplanationQueryService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @Deprecated
    @GetMapping("/place")
    public ResponseEntity<PlaceListResponse> listPlaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String touristCategory,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "LATEST") String sort
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_LIST);
        return ResponseEntity.ok(placeQueryService.listPlaces(new PlaceSearchCondition(
                page,
                limit,
                keyword,
                category,
                touristCategory,
                latitude,
                longitude,
                radiusKm,
                sort
        )));
    }

    @Deprecated
    @GetMapping("/place/{id}")
    public ResponseEntity<PlaceDetailResponse> getPlace(@PathVariable("id") Long placeId) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_DETAIL);
        return ResponseEntity.ok(placeQueryService.getPlace(placeId));
    }

    @Deprecated
    @GetMapping("/place/recommendations")
    public ResponseEntity<PlaceRecommendationResponse> recommendPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(required = false) String recommendationVersion,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = user != null ? user.userId() : null;
        return ResponseEntity.ok(
                placeRecommendationQueryService.recommendPlaces(
                        userId,
                        latitude,
                        longitude,
                        limit,
                        radiusKm,
                        recommendationVersion
                )
        );
    }

    @Deprecated
    @PostMapping("/place/recommendations/click")
    @RateLimited(RateLimitAction.RECOMMENDATION_CLICK)
    public ResponseEntity<PlaceRecommendationClickResponse> recordRecommendationClick(
            @Valid @RequestBody PlaceRecommendationClickRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        Long userId = authenticatedUserId(user);
        placeRecommendationClickService.recordClick(
                userId,
                request.placeId(),
                request.recommendationVersion(),
                request.requestId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlaceRecommendationClickResponse(request.placeId(), "추천 장소 클릭을 기록했습니다."));
    }

    @Deprecated
    @GetMapping("/place/recommendations/{requestId}/explanation")
    public ResponseEntity<PlaceRecommendationExplanationResponse> getRecommendationExplanation(
            @PathVariable String requestId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeRecommendationExplanationQueryService.getExplanation(
                authenticatedUserId(user),
                requestId
        ));
    }

    @Deprecated
    @PostMapping("/map/places/coordinates")
    public ResponseEntity<PlaceCoordinateCreateResponse> createCoordinates(
            @Valid @RequestBody PlaceCoordinateCreateRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_COORDINATE_CREATE);
        PlaceCoordinateCreateResponse response = mapPlaceService.createCoordinateToken(
                request.baseLatitude(),
                request.baseLongitude(),
                request.kakaoPlaceId(),
                authenticatedUserId(user)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @PostMapping("/map/places/upload")
    public ResponseEntity<PlaceCreateResponse> upload(
            @Valid @RequestBody PlaceUploadRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_UPLOAD);
        PlaceCreateResponse response = mapPlaceService.uploadPlaceByToken(
                request.kakaoPlaceId(),
                request.name(),
                request.address(),
                request.roadAddress(),
                request.jibunAddress(),
                request.postalCode(),
                request.category(),
                request.imageUrl(),
                request.englishName(),
                request.touristSummary(),
                request.touristCategories(),
                request.coordinateToken(),
                authenticatedUserId(user)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @DeleteMapping("/map/places/{id}/delete")
    public ResponseEntity<String> deletePlace(
            @PathVariable("id") Long placeId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_DELETE);
        mapPlaceService.deletePlace(placeId, authenticatedUserId(user));
        return ResponseEntity.ok("장소를 삭제했습니다.");
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user.userId();
    }
}
