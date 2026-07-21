package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaCreateRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeListResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.explanation.PlaceRecommendationExplanationQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceSearchCondition;
import com.typenull.pingdom.place.application.service.place.PlaceMediaService;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingNoticeService;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Validated
@Tag(name = "App Place", description = "앱용 장소 API")
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final PlaceRecommendationQueryService placeRecommendationQueryService;
    private final PlaceRecommendationClickService placeRecommendationClickService;
    private final PlaceRecommendationExplanationQueryService placeRecommendationExplanationQueryService;
    private final PlaceMediaService placeMediaService;
    private final PlaceOperatingNoticeService placeOperatingNoticeService;

    @GetMapping
    @Operation(summary = "장소 목록 조회", description = "앱에서 사용할 장소 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceListResponse.class))
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
    public ResponseEntity<PlaceListResponse> listPlaces(
            @Parameter(description = "페이지 번호", example = "1")
            @Min(value = 1, message = "page는 1 이상이어야 합니다.")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 100, message = "limit는 100 이하여야 합니다.")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "장소명 또는 주소 검색어", example = "카페")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "카테고리 필터", example = "카페")
            @RequestParam(required = false) String category,
            @Parameter(description = "관광 카테고리 필터", example = "K_POP")
            @RequestParam(required = false) String touristCategory,
            @Parameter(description = "현재 위도. 거리 검색 시 longitude, radiusKm와 함께 전달합니다.", example = "35.1801")
            @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
            @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
            @RequestParam(required = false) Double latitude,
            @Parameter(description = "현재 경도. 거리 검색 시 latitude, radiusKm와 함께 전달합니다.", example = "128.1078")
            @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
            @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
            @RequestParam(required = false) Double longitude,
            @Parameter(description = "검색 반경(km). 거리 검색 시 latitude, longitude와 함께 전달합니다.", example = "3.0")
            @DecimalMin(value = "0.1", message = "radiusKm는 0.1 이상이어야 합니다.")
            @DecimalMax(value = "20.0", message = "radiusKm는 20.0 이하여야 합니다.")
            @RequestParam(required = false) Double radiusKm,
            @Parameter(description = "정렬 기준. LATEST, NEAREST 또는 POPULAR", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") String sort
    ) {
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

    @GetMapping("/autocomplete")
    @Operation(summary = "장소 검색 자동완성", description = "검색어 입력 중 장소 후보를 자동완성으로 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 자동완성 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceAutocompleteResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "latitude와 longitude는 함께 전달해야 합니다."
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
    public ResponseEntity<PlaceAutocompleteResponse> autocompletePlaces(
            @Parameter(description = "검색어", example = "진주")
            @RequestParam String keyword,
            @Parameter(description = "최대 반환 개수", example = "10")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "현재 위도", example = "35.1801")
            @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
            @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
            @RequestParam(required = false) Double latitude,
            @Parameter(description = "현재 경도", example = "128.1078")
            @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
            @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
            @RequestParam(required = false) Double longitude
    ) {
        return ResponseEntity.ok(placeQueryService.autocompletePlaces(keyword, limit, latitude, longitude));
    }

    @GetMapping("/{placeId}/operating-notices")
    @Operation(summary = "장소 활성 운영 상태 공지 조회", description = "현재 영업시간 기준 운영 여부와 활성 운영 상태 공지를 조회합니다.")
    public ResponseEntity<PlaceOperatingNoticeListResponse> listOperatingNotices(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.listActive(placeId));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "장소 추천 조회", description = "현재 위치와 사용자 반응 이력을 기반으로 추천 장소를 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 추천 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceRecommendationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "좌표 또는 요청값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "recommendPlaces.latitude: 위도는 -90.0 이상이어야 합니다."
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
    public ResponseEntity<PlaceRecommendationResponse> recommendPlaces(
            @Parameter(description = "현재 위도", example = "35.1801")
            @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
            @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
            @RequestParam double latitude,
            @Parameter(description = "현재 경도", example = "128.1078")
            @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
            @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
            @RequestParam double longitude,
            @Parameter(description = "추천 최대 개수", example = "10")
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "초기 탐색 반경(km)", example = "5.0")
            @DecimalMin(value = "1.0", message = "radiusKm는 1.0 이상이어야 합니다.")
            @DecimalMax(value = "20.0", message = "radiusKm는 20.0 이하여야 합니다.")
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @Parameter(description = "강제 추천 버전", example = "place-rec-v2")
            @RequestParam(required = false) String recommendationVersion,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
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

    @PostMapping("/recommendations/click")
    @Operation(summary = "추천 장소 클릭 기록", description = "추천 목록에서 사용자가 선택한 장소 클릭을 기록합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "추천 장소 클릭 기록 성공",
                    content = @Content(schema = @Schema(implementation = PlaceRecommendationClickResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "placeId는 필수입니다."
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
            )
    })
    @RateLimited(RateLimitAction.RECOMMENDATION_CLICK)
    public ResponseEntity<PlaceRecommendationClickResponse> recordRecommendationClick(
            @Valid @RequestBody PlaceRecommendationClickRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
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

    @GetMapping("/recommendations/{requestId}/explanation")
    @Operation(summary = "추천 설명 조회", description = "사용자가 본인 추천 응답 requestId의 후보 source, score, ranking 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 설명 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceRecommendationExplanationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "추천 설명 정보를 찾을 수 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "추천 설명 정보를 찾을 수 없습니다.",
                                              "code": "RECOMMENDATION_EXPLANATION_NOT_FOUND"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<PlaceRecommendationExplanationResponse> getRecommendationExplanation(
            @Parameter(description = "추천 응답 requestId", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
            @PathVariable String requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(placeRecommendationExplanationQueryService.getExplanation(user.userId(), requestId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "장소 상세 조회", description = "특정 장소의 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceDetailResponse.class))
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
            )
    })
    public ResponseEntity<PlaceDetailResponse> getPlace(
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable("id") Long placeId
    ) {
        return ResponseEntity.ok(placeQueryService.getPlace(placeId));
    }

    @PostMapping("/{id}/media/exploration")
    @Operation(summary = "장소 탐색용 미디어 등록", description = "장소 소유자가 탐색 화면에 노출할 미디어를 등록합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "탐색용 미디어 등록 성공",
                    content = @Content(schema = @Schema(implementation = PlaceMediaItem.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 소유가 아닌 장소 미디어 관리 시도",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "자신의 장소 미디어만 관리할 수 있습니다.",
                                              "code": "OTHERS_PLACE_MEDIA_NOT_MANAGED"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<PlaceMediaItem> createExplorationMedia(
            @Parameter(description = "장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody PlaceMediaCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeMediaService.createExplorationMedia(placeId, user.userId(), request));
    }

    @GetMapping("/{id}/media/exploration")
    @Operation(summary = "장소 탐색용 미디어 조회", description = "탐색 화면에 노출할 장소 미디어만 조회합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "탐색용 미디어 조회 성공",
            content = @Content(schema = @Schema(implementation = PlaceMediaResponse.class))
    )
    public ResponseEntity<PlaceMediaResponse> getExplorationMedia(
            @Parameter(description = "장소 ID", example = "1") @PathVariable("id") Long placeId
    ) {
        return ResponseEntity.ok(placeMediaService.getExplorationMedia(placeId));
    }

    @GetMapping("/{id}/media/verification")
    @Operation(summary = "장소 검증용 미디어 조회", description = "장소 소유자가 검증 출처로 기록된 미디어를 조회합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "검증용 미디어 조회 성공",
            content = @Content(schema = @Schema(implementation = PlaceMediaResponse.class))
    )
    public ResponseEntity<PlaceMediaResponse> getVerificationMedia(
            @Parameter(description = "장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeMediaService.getVerificationMedia(placeId, user.userId()));
    }

    @DeleteMapping("/{id}/media/exploration/{mediaId}")
    @Operation(summary = "장소 탐색용 미디어 삭제", description = "장소 소유자가 탐색용 미디어를 삭제합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "탐색용 미디어 삭제 성공",
            content = @Content(
                    examples = @ExampleObject(value = "\"장소 탐색용 미디어를 삭제했습니다.\"")
            )
    )
    public ResponseEntity<String> deleteExplorationMedia(
            @Parameter(description = "장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Parameter(description = "장소 미디어 ID", example = "10") @PathVariable Long mediaId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        placeMediaService.deleteExplorationMedia(placeId, mediaId, user.userId());
        return ResponseEntity.ok("장소 탐색용 미디어를 삭제했습니다.");
    }
}
