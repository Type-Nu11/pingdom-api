package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateRequest;
import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceCreateResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceUploadRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceSearchCondition;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationExplanationQueryService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationQueryService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
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

    @Deprecated
    @GetMapping("/place")
    public ResponseEntity<PlaceListResponse> listPlaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "LATEST") String sort
    ) {
        return ResponseEntity.ok(placeQueryService.listPlaces(new PlaceSearchCondition(
                page,
                limit,
                keyword,
                category,
                latitude,
                longitude,
                radiusKm,
                sort
        )));
    }

    @Deprecated
    @GetMapping("/place/{id}")
    public ResponseEntity<PlaceDetailResponse> getPlace(@PathVariable("id") Long placeId) {
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
    public ResponseEntity<PlaceRecommendationClickResponse> recordRecommendationClick(
            @Valid @RequestBody PlaceRecommendationClickRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        placeRecommendationClickService.recordClick(
                user.userId(),
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
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(placeRecommendationExplanationQueryService.getExplanation(user.userId(), requestId));
    }

    @Deprecated
    @PostMapping("/map/places/coordinates")
    public ResponseEntity<PlaceCoordinateCreateResponse> createCoordinates(
            @Valid @RequestBody PlaceCoordinateCreateRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        PlaceCoordinateCreateResponse response = mapPlaceService.createCoordinateToken(
                request.baseLatitude(),
                request.baseLongitude(),
                request.kakaoPlaceId(),
                user.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @PostMapping("/map/places/upload")
    public ResponseEntity<PlaceCreateResponse> upload(
            @Valid @RequestBody PlaceUploadRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        PlaceCreateResponse response = mapPlaceService.uploadPlaceByToken(
                request.kakaoPlaceId(),
                request.name(),
                request.address(),
                request.category(),
                request.imageUrl(),
                request.coordinateToken(),
                user.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @DeleteMapping("/map/places/{id}/delete")
    public ResponseEntity<String> deletePlace(
            @PathVariable("id") Long placeId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        mapPlaceService.deletePlace(placeId, user.userId());
        return ResponseEntity.ok("장소를 삭제했습니다.");
    }
}
