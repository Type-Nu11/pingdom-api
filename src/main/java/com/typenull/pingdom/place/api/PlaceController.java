package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickRequest;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationClickResponse;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.application.service.place.PlaceSearchCondition;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationQueryService;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/place")
@RequiredArgsConstructor
@Validated
@Tag(name = "App Place", description = "앱용 장소 조회 API")
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final PlaceRecommendationQueryService placeRecommendationQueryService;
    private final PlaceRecommendationClickService placeRecommendationClickService;

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
            @Parameter(description = "정렬 기준. LATEST 또는 NEAREST", example = "LATEST")
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
            @RequestParam(required = false) Double latitude,
            @Parameter(description = "현재 경도", example = "128.1078")
            @RequestParam(required = false) Double longitude
    ) {
        return ResponseEntity.ok(placeQueryService.autocompletePlaces(keyword, limit, latitude, longitude));
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
}
