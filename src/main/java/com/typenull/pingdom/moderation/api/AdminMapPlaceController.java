package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationSnapshotResyncResponse;
import com.typenull.pingdom.moderation.application.query.AdminMapPlaceQueryService;
import com.typenull.pingdom.moderation.application.service.AdminMapPlaceService;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMapPlaceController {

    private final AdminMapPlaceQueryService adminMapPlaceQueryService;
    private final AdminMapPlaceService adminMapPlaceService;
    private final RecommendationMetrics recommendationMetrics;

    @GetMapping
    @Operation(
            summary = "관리자 장소 목록 조회",
            description = "관리자가 등록된 장소 목록을 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 목록 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "places": [
                                                {
                                                  "id": 1,
                                                  "name": "진주성",
                                                  "address": "경상남도 진주시 남강로 626",
                                                  "category": "관광",
                                                  "categoryName": "관광",
                                                  "latitude": 35.1894,
                                                  "longitude": 128.0789,
                                                  "userId": 3,
                                                  "registrant": "placeRegistrar",
                                                  "placeGrowth": {
                                                    "photoCount": 10,
                                                    "level": 5,
                                                    "currentLevelMinPhotoCount": 10,
                                                    "nextLevelMinPhotoCount": 16,
                                                    "progressPercent": 0
                                                  }
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
                    responseCode = "400",
                    description = "지원하지 않는 장소 정렬 기준",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "장소 목록은 LATEST 또는 OLDEST 정렬만 지원합니다.",
                                              "code": "UNSUPPORTED_PLACE_SORT_PARAM"
                                            }
                                            """
                            )
                    )
            )
    })
    public AdminMapPlaceResponse listPlaces(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(
                    description = "장소 정렬 기준. LATEST, OLDEST만 지원합니다.",
                    example = "LATEST",
                    schema = @Schema(type = "string", allowableValues = {"LATEST", "OLDEST"})
            )
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "장소 검색 키워드. 장소명, 등록자 ID, 주소로 검색합니다.", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return adminMapPlaceQueryService.listPlaces(page, limit, sortParam, keyword);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "관리자 장소 상세 조회",
            description = "관리자가 특정 장소의 기본 정보와 연결된 게시글 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": 1,
                                              "name": "진주성",
                                              "address": "경상남도 진주시 남강로 626",
                                              "category": "관광",
                                              "categoryName": "관광",
                                              "latitude": 35.1894,
                                              "longitude": 128.0789,
                                              "userId": 3,
                                              "username": "placeOwner",
                                              "sortParam": "LATEST",
                                              "postCount": 1,
                                              "placeGrowth": {
                                                "photoCount": 10,
                                                "level": 5,
                                                "currentLevelMinPhotoCount": 10,
                                                "nextLevelMinPhotoCount": 16,
                                                "progressPercent": 0
                                              },
                                              "posts": [
                                                {
                                                  "id": 10,
                                                  "imageUrl": "https://example.com/image.jpg",
                                                  "title": "야경 사진",
                                                  "description": "남강 야경입니다.",
                                                  "userId": 3,
                                                  "username": "pingdom_user",
                                                  "createdAt": "2026-05-28T12:00:00",
                                                  "likeCount": 5
                                                }
                                              ]
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
    public AdminMapPlaceDetailResponse getPlace(
            @Parameter(description = "조회할 장소 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "게시글 정렬 기준", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") SortParam sortParam,
            @Parameter(description = "게시글 검색", example = "용인")
            @RequestParam(required = false, defaultValue = "") String keyword
    ) {
        return adminMapPlaceQueryService.getPlace(id, sortParam, keyword);
    }

    @GetMapping("/recommendation-metrics")
    @Operation(
            summary = "관리자 추천 성과 조회",
            description = "관리자가 장소별 추천 노출, 클릭, CTR 지표를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 성과 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPlaceRecommendationMetricsResponse.class)
                    )
            )
    })
    public AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            @Parameter(description = "조회할 페이지 번호. 1 이상으로 보정됩니다.", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "조회할 최대 개수. 1~100 범위로 보정됩니다.", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(
                    description = "추천 성과 정렬 기준",
                    example = "SMOOTHED_CTR",
                    schema = @Schema(type = "string", allowableValues = {
                            "SMOOTHED_CTR", "RAW_CTR", "BOOKMARK_CONVERSION", "LIKE_CONVERSION",
                            "TOTAL_CONVERSION", "EXPOSURE", "CLICK", "UPDATED_AT"
                    })
            )
            @RequestParam(defaultValue = "SMOOTHED_CTR") RecommendationMetricSortBy sortBy,
            @Parameter(description = "장소 검색 키워드", example = "진주")
            @RequestParam(required = false, defaultValue = "") String keyword,
            @Parameter(description = "추천 버전 필터", example = "place-rec-v1")
            @RequestParam(required = false, defaultValue = "") String recommendationVersion,
            @Parameter(description = "최근 N일 기준 필터", example = "7")
            @RequestParam(required = false) Integer days
    ) {
        return adminMapPlaceQueryService.listRecommendationMetrics(
                page,
                limit,
                sortBy,
                keyword,
                recommendationVersion,
                days
        );
    }

    @GetMapping("/recommendation-metrics/compare")
    @Operation(
            summary = "관리자 추천 버전 성과 비교",
            description = "관리자가 두 추천 버전의 CTR, 전환율, 노출 지표를 같은 조건에서 비교합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 버전 성과 비교 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPlaceRecommendationMetricsCompareResponse.class)
                    )
            )
    })
    public AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            @Parameter(description = "기준 추천 버전", example = "place-rec-v1")
            @RequestParam String baselineVersion,
            @Parameter(description = "비교 대상 추천 버전", example = "place-rec-v2")
            @RequestParam String targetVersion,
            @Parameter(description = "장소 검색 키워드", example = "진주")
            @RequestParam(required = false, defaultValue = "") String keyword,
            @Parameter(description = "최근 N일 기준 필터", example = "7")
            @RequestParam(required = false) Integer days
    ) {
        return adminMapPlaceQueryService.compareRecommendationMetrics(
                baselineVersion,
                targetVersion,
                keyword,
                days
        );
    }

    @GetMapping("/duplicates")
    @Operation(
            summary = "관리자 중복 장소 목록 조회",
            description = "관리자가 병합 대상이 될 수 있는 중복 장소 그룹을 조회합니다."
    )
    public AdminMapPlaceDuplicateResponse listDuplicatePlaces() {
        return adminMapPlaceQueryService.listDuplicatePlaces();
    }

    @GetMapping("/duplicates/{id}")
    @Operation(
            summary = "관리자 중복 장소 상세 조회",
            description = "관리자가 특정 장소의 중복 후보 목록을 조회합니다."
    )
    public AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(
            @Parameter(description = "중복 후보를 확인할 장소 ID", example = "10")
            @PathVariable("id") Long placeId
    ) {
        return adminMapPlaceQueryService.getDuplicatePlace(placeId);
    }

    @PostMapping("/merge")
    @Operation(
            summary = "관리자 중복 장소 병합",
            description = "관리자가 중복 장소의 참조 데이터를 대상 장소로 옮기고 원본 장소를 병합합니다."
    )
    public ResponseEntity<AdminMapPlaceMergeResponse> mergePlaces(
            @RequestBody AdminMapPlaceMergeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        AdminMapPlaceMergeResponse response = adminMapPlaceService.mergePlaces(adminUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/merge-histories")
    @Operation(
            summary = "관리자 장소 병합 이력 조회",
            description = "관리자가 최근 장소 병합 이력을 조회합니다."
    )
    public AdminPlaceMergeHistoryResponse listMergeHistories() {
        return adminMapPlaceService.listMergeHistories();
    }

    @PostMapping("/merge-histories/{historyId}/restore")
    @Operation(
            summary = "관리자 장소 병합 복구",
            description = "관리자가 저장된 장소 병합 이력을 기준으로 복구합니다."
    )
    public ResponseEntity<AdminPlaceMergeRestoreResponse> restoreMerge(
            @PathVariable Long historyId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.restoreMerge(adminUserId, historyId));
    }

    @PatchMapping("/{id}/coordinates")
    @Operation(
            summary = "관리자 장소 좌표 수정",
            description = "관리자가 잘못 등록된 장소의 위도와 경도를 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "장소 좌표 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceCoordinateUpdateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeId": 1,
                                              "latitude": 35.1796,
                                              "longitude": 128.1076,
                                              "message": "장소 좌표를 수정했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "latitude": "위도는 90.0 이하여야 합니다."
                                              }
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
    public ResponseEntity<AdminMapPlaceCoordinateUpdateResponse> updatePlaceCoordinates(
            @Parameter(description = "좌표를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceCoordinateUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceCoordinates(adminUserId, placeId, request));
    }

    @PatchMapping("/{id}/kakao-place-id")
    @Operation(
            summary = "관리자 장소 Kakao place id 수정",
            description = "관리자가 장소에 연결된 Kakao place id를 재연결하거나 해제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Kakao place id 수정 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminMapPlaceKakaoPlaceIdUpdateResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeId": 1,
                                              "kakaoPlaceId": "27414316",
                                              "message": "장소 Kakao place id를 수정했습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "입력값을 확인해주세요.",
                                              "errors": {
                                                "kakaoPlaceId": "카카오 장소 ID는 50자 이하여야 합니다."
                                              }
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
                    description = "이미 다른 장소에 연결된 Kakao place id",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "이미 다른 장소에 연결된 Kakao place id입니다.",
                                              "code": "PLACE_KAKAO_PLACE_ID_CONFLICT"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminMapPlaceKakaoPlaceIdUpdateResponse> updatePlaceKakaoPlaceId(
            @Parameter(description = "Kakao place id를 수정할 장소 ID", example = "1") @PathVariable("id") Long placeId,
            @Valid @RequestBody AdminMapPlaceKakaoPlaceIdUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminMapPlaceService.updatePlaceKakaoPlaceId(adminUserId, placeId, request));
    }

    @DeleteMapping("/{id}/delete")
    @Operation(
            summary = "관리자 장소 삭제",
            description = "관리자가 장소를 강제로 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "장소 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
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
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "관리자 권한이 필요합니다.",
                                              "code": "ACCESS_DENIED"
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
    public ResponseEntity<Void> forceDeletePlace(
            @Parameter(description = "강제 삭제할 장소 ID", example = "5") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        adminMapPlaceService.deletePlace(id, adminUserId);
        if (adminUser != null) {
            log.info("Admin force deleted place. adminUserId={}, placeId={}", adminUser.userId(), id);
        } else {
            log.info("Admin force deleted place. adminUserId=unknown, placeId={}", id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recommendation-snapshots/resync")
    @Operation(
            summary = "관리자 추천 snapshot 재동기화",
            description = "관리자가 모든 장소 추천 snapshot을 현재 데이터 기준으로 다시 동기화합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 snapshot 재동기화 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPlaceRecommendationSnapshotResyncResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "placeCount": 12,
                                              "synchronizedSnapshotCount": 12,
                                              "deletedSnapshotCount": 1,
                                              "synchronizedSimilaritySnapshotCount": 24,
                                              "deletedSimilaritySnapshotCount": 2,
                                              "synchronizedVersionSnapshotCount": 3,
                                              "deletedVersionSnapshotCount": 0,
                                              "message": "장소 추천 snapshot 재동기화를 완료했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AdminPlaceRecommendationSnapshotResyncResponse> resyncRecommendationSnapshots(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        String adminUserId = adminUser == null ? "unknown" : String.valueOf(adminUser.userId());
        PlaceRecommendationSnapshotResyncService.SnapshotResyncResult result;

        try {
            result = adminMapPlaceService.resyncRecommendationSnapshots();
            recommendationMetrics.recordSnapshotResyncSuccess(result);
        } catch (RuntimeException exception) {
            recommendationMetrics.recordSnapshotResyncFailure(exception);
            log.error("Admin recommendation snapshot resync failed. adminUserId={}", adminUserId, exception);
            throw exception;
        }

        log.info(
                "Admin resynced place recommendation snapshots. adminUserId={}, placeCount={}, synchronizedSnapshotCount={}, deletedSnapshotCount={}, synchronizedSimilaritySnapshotCount={}, deletedSimilaritySnapshotCount={}, synchronizedVersionSnapshotCount={}, deletedVersionSnapshotCount={}",
                adminUserId,
                result.placeCount(),
                result.synchronizedSnapshotCount(),
                result.deletedSnapshotCount(),
                result.synchronizedSimilaritySnapshotCount(),
                result.deletedSimilaritySnapshotCount(),
                result.synchronizedVersionSnapshotCount(),
                result.deletedVersionSnapshotCount()
        );

        return ResponseEntity.ok(new AdminPlaceRecommendationSnapshotResyncResponse(
                result.placeCount(),
                result.synchronizedSnapshotCount(),
                result.deletedSnapshotCount(),
                result.synchronizedSimilaritySnapshotCount(),
                result.deletedSimilaritySnapshotCount(),
                result.synchronizedVersionSnapshotCount(),
                result.deletedVersionSnapshotCount(),
                "장소 추천 snapshot 재동기화를 완료했습니다."
        ));
    }
}
