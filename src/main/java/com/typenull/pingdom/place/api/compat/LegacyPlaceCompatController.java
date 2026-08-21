package com.typenull.pingdom.place.api.compat;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
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

    private final PlaceRecommendationQueryService placeRecommendationQueryService;
    private final PlaceRecommendationClickService placeRecommendationClickService;
    private final PlaceRecommendationExplanationQueryService placeRecommendationExplanationQueryService;
    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @Deprecated
    @GetMapping("/place/recommendations")
    public ResponseEntity<PlaceRecommendationResponse> recommendPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(required = false) String recommendationVersion,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_RECOMMENDATIONS_GET);
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
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_RECOMMENDATIONS_CLICK);
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
            @CurrentUser JwtAuthenticatedUser user
    ) {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.PLACE_RECOMMENDATION_EXPLANATION_GET);
        return ResponseEntity.ok(placeRecommendationExplanationQueryService.getExplanation(
                authenticatedUserId(user),
                requestId
        ));
    }

    private Long authenticatedUserId(JwtAuthenticatedUser user) {
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user.userId();
    }
}
