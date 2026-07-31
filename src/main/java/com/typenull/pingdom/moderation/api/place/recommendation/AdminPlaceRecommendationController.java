package com.typenull.pingdom.moderation.api.place.recommendation;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.explanation.AdminPlaceRecommendationExplanationResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.snapshot.AdminPlaceRecommendationSnapshotResyncResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateResponse;
import com.typenull.pingdom.moderation.application.query.place.management.AdminMapPlaceQueryService;
import com.typenull.pingdom.moderation.application.query.place.recommendation.AdminPlaceRecommendationExplanationQueryService;
import com.typenull.pingdom.moderation.application.service.place.recommendation.AdminPlaceRecommendationPolicyService;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceRecommendationController {

    private final AdminMapPlaceQueryService adminMapPlaceQueryService;
    private final AdminPlaceRecommendationExplanationQueryService adminPlaceRecommendationExplanationQueryService;
    private final AdminPlaceRecommendationPolicyService adminPlaceRecommendationPolicyService;
    private final RecommendationMetrics recommendationMetrics;

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

    @GetMapping("/recommendations/{requestId}/explanation")
    @Operation(
            summary = "관리자 추천 설명 조회",
            description = "관리자가 추천 응답 requestId의 후보 source, score, ranking, feature log 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "추천 설명 조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = AdminPlaceRecommendationExplanationResponse.class)
                    )
            )
    })
    public AdminPlaceRecommendationExplanationResponse getRecommendationExplanation(
            @Parameter(description = "추천 응답 requestId", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
            @PathVariable String requestId
    ) {
        return adminPlaceRecommendationExplanationQueryService.getExplanation(requestId);
    }

    @PatchMapping("/recommendation-traffic")
    @Operation(
            summary = "관리자 추천 버전 트래픽 비율 수정",
            description = "관리자가 추천 버전별 traffic percentage를 수정하고 즉시 반영합니다."
    )
    public ResponseEntity<AdminPlaceRecommendationTrafficUpdateResponse> updateRecommendationTraffic(
            @Valid @RequestBody AdminPlaceRecommendationTrafficUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser adminUser
    ) {
        Long adminUserId = adminUser == null ? null : adminUser.userId();
        return ResponseEntity.ok(adminPlaceRecommendationPolicyService.updateRecommendationTraffic(adminUserId, request));
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
            result = adminPlaceRecommendationPolicyService.resyncRecommendationSnapshots();
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
