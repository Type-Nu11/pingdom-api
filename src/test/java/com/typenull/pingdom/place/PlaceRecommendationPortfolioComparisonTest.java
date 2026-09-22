package com.typenull.pingdom.place;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;
import com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@Transactional
class PlaceRecommendationPortfolioComparisonTest {

    private static final double REQUEST_LATITUDE = 35.1802d;
    private static final double REQUEST_LONGITUDE = 128.1073d;
    private static final int TOP_K = 3;
    private static final double RADIUS_KM = 5.0d;
    private static final int SIMULATED_EXPOSURE_COUNT = 100;

    @Autowired
    private PlaceRecommendationQueryService placeRecommendationQueryService;

    @Autowired
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Autowired
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Autowired
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Autowired
    private PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;

    @Autowired
    private PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private S3Client s3Client;

    /** 추천 이벤트·집계·사용자 반응과 장소를 비워 두 비교 시나리오가 기존 DB 데이터에 의존하지 않게 함. */
    @BeforeEach
    void setUp() {
        resetData();
    }

    /**
     * 고정 fixture에서 현재 추천의 관련 후보 순위와 상위 결과 다양성을 테스트 내부 기준 엔진과 비교.
     * 클릭·북마크 전환율은 관련 후보 순위에 미리 정한 비율을 대입한 모의 수치. 실제 사용자 지표 개선은 검증 범위에서 제외.
     */
    @Test
    void comparesSyntheticRecommendationScenarios() {
        BaselineRecommendationEngine baselineRecommendationEngine = new BaselineRecommendationEngine();

        PersonalizationScenario personalizationScenario = seedPersonalizationScenario();
        ScenarioMetrics personalizationBaseline = measureScenario(
                baselineRecommendationEngine.recommend(personalizationScenario.userId(), REQUEST_LATITUDE, REQUEST_LONGITUDE, TOP_K, RADIUS_KM),
                personalizationScenario.relevantPlaceIds()
        );
        ScenarioMetrics personalizationCurrent = measureScenario(
                currentRecommendation(personalizationScenario.userId(), REQUEST_LATITUDE, REQUEST_LONGITUDE, TOP_K, RADIUS_KM),
                personalizationScenario.relevantPlaceIds()
        );
        FunnelMetrics personalizationBaselineFunnel = simulateFunnel(
                personalizationBaseline,
                personalizationScenario.relevantPlaceIds(),
                SIMULATED_EXPOSURE_COUNT
        );
        FunnelMetrics personalizationCurrentFunnel = simulateFunnel(
                personalizationCurrent,
                personalizationScenario.relevantPlaceIds(),
                SIMULATED_EXPOSURE_COUNT
        );

        resetData();

        DiversityScenario diversityScenario = seedDiversityScenario();
        ScenarioMetrics diversityBaseline = measureScenario(
                baselineRecommendationEngine.recommend(null, REQUEST_LATITUDE, REQUEST_LONGITUDE, 2, RADIUS_KM),
                diversityScenario.relevantPlaceIds()
        );
        ScenarioMetrics diversityCurrent = measureScenario(
                currentRecommendation(null, REQUEST_LATITUDE, REQUEST_LONGITUDE, 2, RADIUS_KM),
                diversityScenario.relevantPlaceIds()
        );

        System.out.println(buildComparisonReport(
                personalizationBaseline,
                personalizationCurrent,
                personalizationScenario.relevantPlaceIds(),
                personalizationBaselineFunnel,
                personalizationCurrentFunnel,
                diversityBaseline,
                diversityCurrent
        ));

        assertTrue(
                bestRelevantRank(personalizationCurrent, personalizationScenario.relevantPlaceIds())
                        < bestRelevantRank(personalizationBaseline, personalizationScenario.relevantPlaceIds()),
                "현재 알고리즘이 관련 후보를 더 높은 순위로 올려야 합니다."
        );
        assertTrue(
                personalizationScenario.relevantPlaceIds().contains(personalizationCurrent.orderedPlaceIds().getFirst()),
                "현재 알고리즘의 1위는 관련 후보여야 합니다."
        );
        assertFalse(
                personalizationScenario.relevantPlaceIds().contains(personalizationBaseline.orderedPlaceIds().getFirst()),
                "baseline의 1위는 관련 후보가 아니어야 합니다."
        );
        assertTrue(
                diversityCurrent.averagePairwiseSimilarity() < diversityBaseline.averagePairwiseSimilarity(),
                "현재 알고리즘이 상위 결과 유사도를 낮춰야 합니다."
        );
        assertTrue(
                personalizationCurrentFunnel.ctr() > personalizationBaselineFunnel.ctr(),
                "현재 알고리즘의 CTR이 더 높아야 합니다."
        );
        assertTrue(
                personalizationCurrentFunnel.bookmarkConversionRate() > personalizationBaselineFunnel.bookmarkConversionRate(),
                "현재 알고리즘의 북마크 전환율이 더 높아야 합니다."
        );
    }

    /** 관측 기록을 동반하는 실제 추천 query를 호출하고 응답 ID 순서대로 장소 엔티티를 복원. */
    private List<MapPlace> currentRecommendation(
            Long userId,
            double latitude,
            double longitude,
            int limit,
            double radiusKm
    ) {
        PlaceRecommendationResponse response = placeRecommendationQueryService.recommendAndRecordObservations(
                userId,
                latitude,
                longitude,
                limit,
                radiusKm,
                null
        );
        List<Long> orderedPlaceIds = response.places().stream()
                .map(item -> item.id())
                .toList();
        Map<Long, MapPlace> placeIndex = mapPlaceRepository.findAllById(orderedPlaceIds).stream()
                .collect(Collectors.toMap(MapPlace::getId, place -> place));

        return orderedPlaceIds.stream()
                .map(placeIndex::get)
                .toList();
    }

    /** 추천 순서·관련 후보 수와 결과 내부 쌍별 유사도를 같은 측정 방식으로 수집. 관련 후보는 fixture가 지정한 ID 집합. */
    private ScenarioMetrics measureScenario(List<MapPlace> orderedPlaces, Set<Long> relevantPlaceIds) {
        List<Long> orderedPlaceIds = orderedPlaces.stream()
                .map(MapPlace::getId)
                .toList();
        int relevantHitCount = (int) orderedPlaceIds.stream()
                .filter(relevantPlaceIds::contains)
                .count();

        Map<Long, MapPlace> placeIndex = orderedPlaces.stream()
                .collect(Collectors.toMap(MapPlace::getId, place -> place));
        PlaceRecommendationSimilarityService.SimilarityContext similarityContext =
                placeRecommendationSimilarityService.buildContext(orderedPlaceIds, placeIndex, false);

        double averagePairwiseSimilarity = averagePairwiseSimilarity(orderedPlaceIds, similarityContext);

        return new ScenarioMetrics(
                orderedPlaceIds,
                orderedPlaces.stream().map(MapPlace::getName).toList(),
                relevantHitCount,
                averagePairwiseSimilarity
        );
    }

    /** 서로 다른 결과의 모든 비순서 쌍을 한 번씩 계산해 평균 유사도를 산출. 결과가 두 개 미만이면 0으로 취급. */
    private double averagePairwiseSimilarity(
            List<Long> orderedPlaceIds,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        if (orderedPlaceIds.size() < 2) {
            return 0d;
        }

        int pairCount = 0;
        double similaritySum = 0d;
        for (int leftIndex = 0; leftIndex < orderedPlaceIds.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < orderedPlaceIds.size(); rightIndex++) {
                similaritySum += placeRecommendationSimilarityService.similarity(
                        orderedPlaceIds.get(leftIndex),
                        orderedPlaceIds.get(rightIndex),
                        similarityContext
                );
                pairCount++;
            }
        }

        return pairCount == 0 ? 0d : similaritySum / pairCount;
    }

    /** fixture가 관련 있다고 지정한 첫 결과의 1부터 시작하는 순위를 반환. 관련 결과가 없으면 최댓값으로 표현. */
    private int bestRelevantRank(ScenarioMetrics metrics, Set<Long> relevantPlaceIds) {
        for (int index = 0; index < metrics.orderedPlaceIds().size(); index++) {
            if (relevantPlaceIds.contains(metrics.orderedPlaceIds().get(index))) {
                return index + 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 관련 후보의 최고 순위에 고정된 클릭·북마크 비율을 대입하고 노출 수에 맞춰 반올림.
     * DB 이벤트나 실제 행동을 측정하지 않으며 순위 개선을 가정한 모의 전환 수치만 생성.
     */
    private FunnelMetrics simulateFunnel(
            ScenarioMetrics metrics,
            Set<Long> relevantPlaceIds,
            int exposureCount
    ) {
        int bestRank = bestRelevantRank(metrics, relevantPlaceIds);
        int clicksPer100 = switch (bestRank) {
            case 1 -> 60;
            case 2 -> 30;
            case 3 -> 15;
            default -> 5;
        };
        int bookmarkConversionsPer100 = switch (bestRank) {
            case 1 -> 18;
            case 2 -> 6;
            case 3 -> 2;
            default -> 0;
        };

        long clickCount = Math.round(exposureCount * (clicksPer100 / 100d));
        long bookmarkConversionCount = Math.round(exposureCount * (bookmarkConversionsPer100 / 100d));

        return new FunnelMetrics(
                exposureCount,
                clickCount,
                bookmarkConversionCount,
                clickCount / (double) exposureCount,
                bookmarkConversionCount / (double) exposureCount
        );
    }

    /**
     * 개인 반응이 있는 seed, 직접 연관 장소, 확장 후보와 주변 인기 장소를 저장.
     * 공동 반응과 수동 유사도 snapshot으로 지정한 두 관련 후보를 현재 추천이 끌어올리는 비교 조건을 구성.
     */
    private PersonalizationScenario seedPersonalizationScenario() {
        long targetUserId = 101L;

        MapPlace seedPlace = createMapPlace("기준 북마크 장소", "경상남도 진주시 강남로 1", 35.1800d, 128.1070d, 2L);
        MapPlace relatedPlace = createMapPlace("직접 연관 장소", "경상남도 진주시 강남로 2", 35.1814d, 128.1085d, 3L);
        MapPlace expansionPlace = createMapPlace("확장 연관 장소", "경상남도 진주시 문산읍 1", 35.1938d, 128.1210d, 5L);
        MapPlace nearbyPopularPlace = createMapPlace("근거리 인기 장소", "경상남도 진주시 강남로 3", 35.1804d, 128.1075d, 4L);
        MapPlace nearbyGeneralPlace = createMapPlace("근거리 일반 장소", "경상남도 진주시 강남로 4", 35.1848d, 128.1122d, 1L);

        MapImage seedImage = createMapImage(seedPlace, 2L, "seed");
        MapImage relatedImage = createMapImage(relatedPlace, 7L, "related");
        MapImage expansionImage = createMapImage(expansionPlace, 12L, "expansion-a");
        createMapImage(expansionPlace, 10L, "expansion-b");
        createMapImage(expansionPlace, 8L, "expansion-c");
        createMapImage(expansionPlace, 6L, "expansion-d");
        createMapImage(expansionPlace, 5L, "expansion-e");
        createMapImage(nearbyPopularPlace, 18L, "popular-a");
        createMapImage(nearbyPopularPlace, 14L, "popular-b");
        createMapImage(nearbyPopularPlace, 11L, "popular-c");
        createMapImage(nearbyPopularPlace, 8L, "popular-d");
        createMapImage(nearbyGeneralPlace, 0L, "general-a");

        createBookmark(targetUserId, seedPlace.getId());
        createLike(targetUserId, seedImage.getId());

        createBookmarkGroup(List.of(201L, 202L, 203L, 204L, 205L), seedPlace.getId(), relatedPlace.getId());
        createLikeGroup(List.of(201L, 202L, 203L, 204L, 205L), seedImage.getId(), relatedImage.getId());

        createBookmarkGroup(List.of(206L, 207L, 208L, 209L, 210L, 211L), relatedPlace.getId(), expansionPlace.getId());
        createLikeGroup(List.of(206L, 207L, 208L, 209L, 210L, 211L), relatedImage.getId(), expansionImage.getId());

        createBookmarkGroup(List.of(301L, 302L, 303L, 304L, 305L, 306L, 307L), nearbyPopularPlace.getId());
        createBookmarkGroup(List.of(401L, 402L), nearbyGeneralPlace.getId());

        saveSimilaritySnapshot(seedPlace.getId(), relatedPlace.getId(), 0.95d);
        saveSimilaritySnapshot(seedPlace.getId(), expansionPlace.getId(), 0.03d);
        saveSimilaritySnapshot(seedPlace.getId(), nearbyPopularPlace.getId(), 0.04d);
        saveSimilaritySnapshot(seedPlace.getId(), nearbyGeneralPlace.getId(), 0.02d);
        saveSimilaritySnapshot(relatedPlace.getId(), expansionPlace.getId(), 0.98d);
        saveSimilaritySnapshot(relatedPlace.getId(), nearbyPopularPlace.getId(), 0.04d);
        saveSimilaritySnapshot(relatedPlace.getId(), nearbyGeneralPlace.getId(), 0.02d);
        saveSimilaritySnapshot(expansionPlace.getId(), nearbyPopularPlace.getId(), 0.02d);
        saveSimilaritySnapshot(expansionPlace.getId(), nearbyGeneralPlace.getId(), 0.03d);
        saveSimilaritySnapshot(nearbyPopularPlace.getId(), nearbyGeneralPlace.getId(), 0.08d);

        return new PersonalizationScenario(
                targetUserId,
                Set.of(relatedPlace.getId(), expansionPlace.getId()),
                expansionPlace.getId()
        );
    }

    /** 유사도가 높은 두 인기 장소와 별도 후보를 저장해 상위 두 결과가 덜 비슷하게 선택되는지 비교할 조건을 생성. */
    private DiversityScenario seedDiversityScenario() {
        MapPlace duplicatePlaceA = createMapPlace("중복 후보 A", "경상남도 진주시 평거동 10", 35.1802d, 128.1079d, 4L);
        MapPlace duplicatePlaceB = createMapPlace("중복 후보 B", "경상남도 진주시 평거동 11", 35.18025d, 128.10795d, 4L);
        MapPlace diversePlace = createMapPlace("다양성 후보", "경상남도 진주시 충무공동 1", 35.1865d, 128.1145d, 3L);

        MapImage duplicateImageA = createMapImage(duplicatePlaceA, 20L, "dup-a-1");
        createMapImage(duplicatePlaceA, 15L, "dup-a-2");
        createMapImage(duplicatePlaceA, 10L, "dup-a-3");

        MapImage duplicateImageB = createMapImage(duplicatePlaceB, 21L, "dup-b-1");
        createMapImage(duplicatePlaceB, 17L, "dup-b-2");
        createMapImage(duplicatePlaceB, 12L, "dup-b-3");

        MapImage diverseImage = createMapImage(diversePlace, 13L, "diverse-1");
        createMapImage(diversePlace, 9L, "diverse-2");
        createMapImage(diversePlace, 6L, "diverse-3");

        createBookmarkGroup(List.of(501L, 502L, 503L, 504L, 505L, 506L), duplicatePlaceA.getId(), duplicatePlaceB.getId());
        createLikeGroup(List.of(501L, 502L, 503L, 504L, 505L, 506L), duplicateImageA.getId(), duplicateImageB.getId());

        createBookmarkGroup(List.of(601L, 602L, 603L), diversePlace.getId());
        createLikeGroup(List.of(601L, 602L, 603L), diverseImage.getId());

        saveSimilaritySnapshot(duplicatePlaceA.getId(), duplicatePlaceB.getId(), 0.96d);
        saveSimilaritySnapshot(duplicatePlaceA.getId(), diversePlace.getId(), 0.18d);
        saveSimilaritySnapshot(duplicatePlaceB.getId(), diversePlace.getId(), 0.15d);

        return new DiversityScenario(Set.of(duplicatePlaceA.getId(), diversePlace.getId()));
    }

    /** 좋아요·북마크·사진과 추천 이벤트·snapshot을 먼저 삭제한 뒤 장소를 비워 같은 트랜잭션 안에서도 다음 시나리오를 새로 구성. */
    private void resetData() {
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        placeRecommendationConversionRepository.deleteAllInBatch();
        placeRecommendationClickRepository.deleteAllInBatch();
        placeRecommendationExposureRepository.deleteAllInBatch();
        placeRecommendationVersionSnapshotRepository.deleteAllInBatch();
        placeSimilaritySnapshotRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
    }

    /** 비교에 필요한 좌표·이름·주소·사진 수를 지정하고 고정 등록자 ID로 장소 fixture를 저장. */
    private MapPlace createMapPlace(String name, String address, double latitude, double longitude, long photoCount) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .userId(1L)
                .registrant("portfolio-tester")
                .photoCount(photoCount)
                .build());
    }

    /** 지정 장소에 좋아요 수를 가진 사진 행을 저장. 파일은 업로드하지 않고 테스트 URL·key만 사용. */
    private MapImage createMapImage(MapPlace mapPlace, long likeCount, String title) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/" + title + ".jpg")
                .s3Key("test/" + title + ".jpg")
                .title(title)
                .description(title + " description")
                .userId(1L)
                .username("portfolio-tester")
                .likeCount(likeCount)
                .mapPlace(mapPlace)
                .build());
    }

    /** 개인화 및 공동 북마크 집계에 사용할 사용자–장소 반응 행을 저장. */
    private void createBookmark(long userId, long placeId) {
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(placeId)
                .build());
    }

    /** 개인화 및 공동 좋아요 집계에 사용할 사용자–사진 반응 행을 저장. */
    private void createLike(long userId, long mapImageId) {
        mapImageLikeRepository.save(MapImageLike.builder()
                .userId(userId)
                .mapImageId(mapImageId)
                .build());
    }

    /** 모든 지정 사용자가 모든 지정 장소를 북마크한 교차 조합을 만들어 공동 반응을 구성. */
    private void createBookmarkGroup(List<Long> userIds, Long... placeIds) {
        for (Long userId : userIds) {
            for (Long placeId : placeIds) {
                createBookmark(userId, placeId);
            }
        }
    }

    /** 모든 지정 사용자가 모든 지정 사진에 좋아요를 누른 교차 조합을 만들어 공동 반응을 구성. */
    private void createLikeGroup(List<Long> userIds, Long... imageIds) {
        for (Long userId : userIds) {
            for (Long imageId : imageIds) {
                createLike(userId, imageId);
            }
        }
    }

    /** 장소 ID를 작은 값부터 정렬하고 전달 점수를 geo·total에 넣어 비교용 유사도 snapshot을 저장. 실제 유사도 산출 과정은 검증 범위에서 제외. */
    private void saveSimilaritySnapshot(Long leftPlaceId, Long rightPlaceId, double totalSimilarityScore) {
        placeSimilaritySnapshotRepository.save(PlaceSimilaritySnapshot.builder()
                .leftPlaceId(Math.min(leftPlaceId, rightPlaceId))
                .rightPlaceId(Math.max(leftPlaceId, rightPlaceId))
                .geoKernelScore(totalSimilarityScore)
                .coBookmarkPmiScore(0d)
                .coLikeCosineScore(0d)
                .trendSimilarityScore(0d)
                .totalSimilarityScore(totalSimilarityScore)
                .updatedAt(LocalDateTime.now())
                .build());
    }

    /** 개인화 순위·모의 전환율과 다양성 유사도를 기준/현재 순서로 출력. 출력된 전환율은 운영 관측값과 구분되는 모의 수치. */
    private String buildComparisonReport(
            ScenarioMetrics personalizationBaseline,
            ScenarioMetrics personalizationCurrent,
            Set<Long> personalizationRelevantPlaceIds,
            FunnelMetrics personalizationBaselineFunnel,
            FunnelMetrics personalizationCurrentFunnel,
            ScenarioMetrics diversityBaseline,
            ScenarioMetrics diversityCurrent
    ) {
        return """
                [Portfolio Comparison]
                Personalization
                - baseline top3: %s
                - current top3: %s
                - best relevant rank: %d -> %d
                - top1 relevant: %s -> %s
                - simulated CTR: %s -> %s
                - simulated bookmark conversion: %s -> %s

                Diversity
                - baseline top2: %s
                - current top2: %s
                - average pairwise similarity: %s -> %s
                """.formatted(
                personalizationBaseline.orderedPlaceNames(),
                personalizationCurrent.orderedPlaceNames(),
                bestRelevantRank(personalizationBaseline, personalizationRelevantPlaceIds),
                bestRelevantRank(personalizationCurrent, personalizationRelevantPlaceIds),
                personalizationRelevantPlaceIds.contains(personalizationBaseline.orderedPlaceIds().getFirst()),
                personalizationRelevantPlaceIds.contains(personalizationCurrent.orderedPlaceIds().getFirst()),
                formatPercent(personalizationBaselineFunnel.ctr()),
                formatPercent(personalizationCurrentFunnel.ctr()),
                formatPercent(personalizationBaselineFunnel.bookmarkConversionRate()),
                formatPercent(personalizationCurrentFunnel.bookmarkConversionRate()),
                diversityBaseline.orderedPlaceNames(),
                diversityCurrent.orderedPlaceNames(),
                formatDouble(diversityBaseline.averagePairwiseSimilarity()),
                formatDouble(diversityCurrent.averagePairwiseSimilarity())
        );
    }

    /** 보고서의 유사도를 소수점 세 자리로 고정하고 실행 환경과 관계없이 점 소수 구분자를 사용. */
    private String formatDouble(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    /** 0~1 비율을 백분율로 바꿔 소수점 한 자리와 퍼센트 기호로 출력. */
    private String formatPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100d);
    }

    private record PersonalizationScenario(Long userId, Set<Long> relevantPlaceIds, Long expansionPlaceId) {
    }

    private record DiversityScenario(Set<Long> relevantPlaceIds) {
    }

    private record ScenarioMetrics(
            List<Long> orderedPlaceIds,
            List<String> orderedPlaceNames,
            int relevantHitCount,
            double averagePairwiseSimilarity
    ) {
    }

    private record FunnelMetrics(
            long exposureCount,
            long clickCount,
            long bookmarkConversionCount,
            double ctr,
            double bookmarkConversionRate
    ) {
    }

    /** 비교용으로 테스트 안에 고정한 거리·인기·개인 반응 기반 추천 구현. 운영 코드의 과거 전체 버전·실제 성과 재현은 비교 범위에서 제외. */
    private final class BaselineRecommendationEngine {
        private static final int MIN_LIMIT = 1;
        private static final int MAX_LIMIT = 20;
        private static final double MIN_RADIUS_KM = 1.0d;
        private static final double MAX_RADIUS_KM = 20.0d;
        private static final double EARTH_RADIUS_METERS = 6_371_000d;
        private static final double PERSONAL_DECAY_METERS = 3_000d;
        private static final double FRESHNESS_DECAY_DAYS = 14d;
        private static final double BAYESIAN_PRIOR_WEIGHT = 3d;

        /**
         * 한도·반경을 제한하고 좌표가 있는 장소에서 기존 반응을 제외한 후보를 고른 뒤 기준 점수로 정렬.
         * 제외로 후보가 없으면 다시 포함하며 동점은 거리, 장소 ID 내림차순으로 결정.
         */
        private List<MapPlace> recommend(
                Long userId,
                double latitude,
                double longitude,
                int limit,
                double radiusKm
        ) {
            int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
            double safeRadiusKm = Math.max(MIN_RADIUS_KM, Math.min(radiusKm, MAX_RADIUS_KM));

            List<MapPlace> allPlaces = mapPlaceRepository.findAll().stream()
                    .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                    .toList();

            if (allPlaces.isEmpty()) {
                return List.of();
            }

            Map<Long, MapPlace> placeIndex = allPlaces.stream()
                    .collect(Collectors.toMap(MapPlace::getId, place -> place));

            UserSignalContext signalContext = loadUserSignals(userId);
            List<PlaceDistance> placeDistances = allPlaces.stream()
                    .map(place -> new PlaceDistance(place, calculateDistanceMeters(
                            latitude,
                            longitude,
                            place.getLatitude(),
                            place.getLongitude()
                    )))
                    .toList();

            CandidateSelection selection = selectCandidates(
                    placeDistances,
                    safeLimit,
                    safeRadiusKm,
                    signalContext.interactedPlaceIds()
            );

            if (selection.candidates().isEmpty() && !signalContext.interactedPlaceIds().isEmpty()) {
                selection = selectCandidates(placeDistances, safeLimit, safeRadiusKm, Set.of());
            }

            if (selection.candidates().isEmpty()) {
                return List.of();
            }

            Map<Long, PlaceAggregate> aggregateMap = loadAggregates(selection.candidates());
            double globalAverageLikePerPhoto = calculateGlobalAverageLikePerPhoto(selection.candidates(), aggregateMap);
            double maxSeedWeight = signalContext.seedWeights().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(1.0d);
            boolean hasPersonalSignals = !signalContext.seedWeights().isEmpty();
            double appliedRadiusKm = selection.appliedRadiusKm();

            return applyFinalScores(selection.candidates().stream()
                    .map(candidate -> toIntermediateCandidate(
                            candidate,
                            aggregateMap.getOrDefault(candidate.place().getId(), new PlaceAggregate()),
                            signalContext,
                            globalAverageLikePerPhoto,
                            maxSeedWeight,
                            appliedRadiusKm,
                            placeIndex
                    ))
                    .toList(), hasPersonalSignals).stream()
                    .sorted(Comparator
                            .comparingDouble(ScoredCandidate::finalScore).reversed()
                            .thenComparingDouble(ScoredCandidate::distanceMeters)
                            .thenComparing(candidate -> candidate.place().getId(), Comparator.reverseOrder()))
                    .limit(safeLimit)
                    .map(ScoredCandidate::place)
                    .toList();
        }

        /** 반경을 단계적으로 넓혀 요구 수 이상 후보를 찾음. 최종 후보가 전혀 없으면 제외 조건을 유지한 가까운 장소 최대 limit×3개로 보완. */
        private CandidateSelection selectCandidates(
                List<PlaceDistance> placeDistances,
                int limit,
                double requestedRadiusKm,
                Set<Long> excludedPlaceIds
        ) {
            double appliedRadiusKm = requestedRadiusKm;
            List<PlaceDistance> candidates = List.of();

            for (double radiusStepKm : buildRadiusSteps(requestedRadiusKm)) {
                appliedRadiusKm = radiusStepKm;
                double radiusMeters = radiusStepKm * 1_000d;

                candidates = placeDistances.stream()
                        .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
                        .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                        .toList();

                if (candidates.size() >= limit) {
                    break;
                }
            }

            if (candidates.isEmpty()) {
                List<PlaceDistance> fallbackCandidates = placeDistances.stream()
                        .filter(candidate -> !excludedPlaceIds.contains(candidate.place().getId()))
                        .sorted(Comparator.comparingDouble(PlaceDistance::distanceMeters))
                        .limit(Math.max(limit * 3L, limit))
                        .toList();

                if (!fallbackCandidates.isEmpty()) {
                    appliedRadiusKm = Math.max(
                            appliedRadiusKm,
                            fallbackCandidates.get(fallbackCandidates.size() - 1).distanceMeters() / 1_000d
                    );
                    candidates = fallbackCandidates;
                }
            }

            return new CandidateSelection(candidates, appliedRadiusKm);
        }

        /** 요청 반경에서 시작해 최대 20km까지 두 배씩 넓히는 중복 없는 검색 단계를 생성. */
        private List<Double> buildRadiusSteps(double requestedRadiusKm) {
            Set<Double> radiusSteps = new LinkedHashSet<>();
            double current = requestedRadiusKm;
            radiusSteps.add(current);

            while (current < MAX_RADIUS_KM) {
                current = Math.min(MAX_RADIUS_KM, current * 2d);
                radiusSteps.add(current);
            }

            return new ArrayList<>(radiusSteps);
        }

        /** 북마크·좋아요·업로드를 각각 1.0·0.6·0.3 가중치로 합산하고 반응 장소를 제외 집합으로 묶음. 익명은 빈 신호로 처리. */
        private UserSignalContext loadUserSignals(Long userId) {
            if (userId == null) {
                return UserSignalContext.empty();
            }

            Map<Long, Double> seedWeights = new HashMap<>();
            Map<Long, PersonalSignalType> signalTypes = new HashMap<>();

            registerSignals(
                    mapBookmarkRepository.findPlaceIdsByUserId(userId),
                    1.0d,
                    PersonalSignalType.BOOKMARK,
                    seedWeights,
                    signalTypes
            );
            registerSignals(
                    mapImageLikeRepository.findPlaceIdsByUserId(userId),
                    0.6d,
                    PersonalSignalType.LIKE,
                    seedWeights,
                    signalTypes
            );
            registerSignals(
                    mapImageRepository.findPlaceIdsByUserId(userId),
                    0.3d,
                    PersonalSignalType.UPLOAD,
                    seedWeights,
                    signalTypes
            );

            return new UserSignalContext(seedWeights, signalTypes, Set.copyOf(seedWeights.keySet()));
        }

        /** 장소별 반응 가중치는 더하고 신호 유형은 우선순위가 높은 하나를 유지. */
        private void registerSignals(
                Collection<Long> placeIds,
                double weight,
                PersonalSignalType signalType,
                Map<Long, Double> seedWeights,
                Map<Long, PersonalSignalType> signalTypes
        ) {
            for (Long placeId : placeIds) {
                seedWeights.merge(placeId, weight, Double::sum);
                signalTypes.merge(placeId, signalType, PersonalSignalType::stronger);
            }
        }

        /** 선택 후보의 북마크 수와 사진 좋아요 합계·최신 시각을 별도 집계 조회로 수집. 누락된 집계는 후속 점수 계산에서 기본값을 사용. */
        private Map<Long, PlaceAggregate> loadAggregates(List<PlaceDistance> candidates) {
            List<Long> placeIds = candidates.stream()
                    .map(candidate -> candidate.place().getId())
                    .toList();

            Map<Long, PlaceAggregate> aggregateMap = new HashMap<>();

            for (MapBookmarkRepository.PlaceBookmarkCountProjection projection :
                    mapBookmarkRepository.findBookmarkCountsByPlaceIds(placeIds)) {
                aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> new PlaceAggregate())
                        .bookmarkCount = projection.getBookmarkCount();
            }

            for (MapImageRepository.PlaceImageAggregateProjection projection :
                    mapImageRepository.findPlaceAggregatesByPlaceIds(placeIds)) {
                aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> new PlaceAggregate())
                        .mergeImageAggregate(projection.getLikeSum(), projection.getLatestCreatedAt());
            }

            return aggregateMap;
        }

        /** 후보별 사진당 좋아요 수를 계산한 뒤 장소 단위 단순 평균을 산출. 사진이 없는 장소도 0으로 평균에 포함. */
        private double calculateGlobalAverageLikePerPhoto(
                List<PlaceDistance> candidates,
                Map<Long, PlaceAggregate> aggregateMap
        ) {
            return candidates.stream()
                    .map(candidate -> {
                        PlaceAggregate aggregate = aggregateMap.getOrDefault(
                                candidate.place().getId(),
                                new PlaceAggregate()
                        );
                        long photoCount = candidate.place().currentPhotoCount();
                        if (photoCount <= 0L) {
                            return 0d;
                        }
                        return (double) aggregate.likeSum / (double) photoCount;
                    })
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0d);
        }

        /**
         * seed와의 거리 감쇠에 정규화한 반응 가중치를 곱한 최댓값을 개인 점수로 사용.
         * 요청 위치 거리 점수와 사전 평균으로 보정한 좋아요·북마크·사진 수 품질 점수, 최신성 점수를 함께 계산.
         */
        private IntermediateCandidate toIntermediateCandidate(
                PlaceDistance candidate,
                PlaceAggregate aggregate,
                UserSignalContext signalContext,
                double globalAverageLikePerPhoto,
                double maxSeedWeight,
                double appliedRadiusKm,
                Map<Long, MapPlace> placeIndex
        ) {
            double personalScore = 0d;
            PersonalSignalType dominantSignalType = PersonalSignalType.NONE;

            for (Map.Entry<Long, Double> seedWeightEntry : signalContext.seedWeights().entrySet()) {
                MapPlace seedPlace = placeIndex.get(seedWeightEntry.getKey());
                if (seedPlace == null || seedPlace.getLatitude() == null || seedPlace.getLongitude() == null) {
                    continue;
                }

                double seedDistanceMeters = calculateDistanceMeters(
                        candidate.place().getLatitude(),
                        candidate.place().getLongitude(),
                        seedPlace.getLatitude(),
                        seedPlace.getLongitude()
                );
                double similarity = Math.exp(-seedDistanceMeters / PERSONAL_DECAY_METERS);
                double normalizedSeedWeight = seedWeightEntry.getValue() / maxSeedWeight;
                double contribution = normalizedSeedWeight * similarity;

                if (contribution > personalScore) {
                    personalScore = contribution;
                    dominantSignalType = signalContext.signalTypes().getOrDefault(
                            seedWeightEntry.getKey(),
                            PersonalSignalType.NONE
                    );
                }
            }

            double geoScore = 1d - Math.min(candidate.distanceMeters() / 1_000d / appliedRadiusKm, 1d);

            long photoCount = candidate.place().currentPhotoCount();
            double smoothedLikeAverage = (aggregate.likeSum + BAYESIAN_PRIOR_WEIGHT * globalAverageLikePerPhoto)
                    / (photoCount + BAYESIAN_PRIOR_WEIGHT);
            double rawQualityScore = smoothedLikeAverage
                    + (Math.log1p(aggregate.bookmarkCount) * 0.35d)
                    + (Math.log1p(photoCount) * 0.20d);
            double freshnessScore = calculateFreshnessScore(aggregate.latestCreatedAt);

            return new IntermediateCandidate(
                    candidate.place(),
                    candidate.distanceMeters(),
                    geoScore,
                    personalScore,
                    rawQualityScore,
                    freshnessScore,
                    dominantSignalType
            );
        }

        /** 후보 품질 점수를 min-max 정규화하고 개인 신호 유무에 따라 고정 비중으로 거리·개인화·품질·최신성을 합산. */
        private List<ScoredCandidate> applyFinalScores(List<IntermediateCandidate> candidates, boolean hasPersonalSignals) {
            double minQuality = candidates.stream()
                    .mapToDouble(IntermediateCandidate::rawQualityScore)
                    .min()
                    .orElse(0d);
            double maxQuality = candidates.stream()
                    .mapToDouble(IntermediateCandidate::rawQualityScore)
                    .max()
                    .orElse(0d);

            return candidates.stream()
                    .map(candidate -> {
                        double normalizedQuality = normalize(candidate.rawQualityScore(), minQuality, maxQuality);
                        double finalScore = hasPersonalSignals
                                ? (0.35d * candidate.geoScore())
                                + (0.35d * candidate.personalScore())
                                + (0.20d * normalizedQuality)
                                + (0.10d * candidate.freshnessScore())
                                : (0.55d * candidate.geoScore())
                                + (0.30d * normalizedQuality)
                                + (0.15d * candidate.freshnessScore());

                        return new ScoredCandidate(
                                candidate.place(),
                                candidate.distanceMeters(),
                                normalizedQuality,
                                finalScore
                        );
                    })
                    .toList();
        }

        /** 최신 사진 경과 시간을 일수로 환산해 14일 감쇠 점수를 계산. 시각이 없으면 0, 미래 시각이면 경과 시간을 0으로 제한. */
        private double calculateFreshnessScore(LocalDateTime latestCreatedAt) {
            if (latestCreatedAt == null) {
                return 0d;
            }

            double days = Math.max(0d, Duration.between(latestCreatedAt, LocalDateTime.now()).toHours() / 24d);
            return Math.exp(-days / FRESHNESS_DECAY_DAYS);
        }

        /** 최솟값·최댓값 사이로 정규화하되 모두 같은 양수면 0.5, 같은 0 이하 값이면 0을 반환. */
        private double normalize(double value, double min, double max) {
            if (Double.compare(min, max) == 0) {
                return max > 0d ? 0.5d : 0d;
            }
            return (value - min) / (max - min);
        }

        /** 두 위경도의 Haversine 거리를 고정 지구 반지름으로 계산해 미터로 반환. */
        private double calculateDistanceMeters(
                double baseLatitude,
                double baseLongitude,
                double targetLatitude,
                double targetLongitude
        ) {
            double latitudeDelta = Math.toRadians(targetLatitude - baseLatitude);
            double longitudeDelta = Math.toRadians(targetLongitude - baseLongitude);
            double baseLatitudeRadians = Math.toRadians(baseLatitude);
            double targetLatitudeRadians = Math.toRadians(targetLatitude);

            double a = Math.sin(latitudeDelta / 2d) * Math.sin(latitudeDelta / 2d)
                    + Math.cos(baseLatitudeRadians) * Math.cos(targetLatitudeRadians)
                    * Math.sin(longitudeDelta / 2d) * Math.sin(longitudeDelta / 2d);
            double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
            return EARTH_RADIUS_METERS * c;
        }

        private enum PersonalSignalType {
            BOOKMARK(3),
            LIKE(2),
            UPLOAD(1),
            NONE(0);

            private final int priority;

            /** 동일 장소에 여러 신호가 있을 때 대표 유형을 고르는 우선순위를 저장. */
            PersonalSignalType(int priority) {
                this.priority = priority;
            }

            /** 우선순위가 큰 신호를 선택하며 같으면 먼저 전달된 값을 유지. */
            private static PersonalSignalType stronger(PersonalSignalType left, PersonalSignalType right) {
                return left.priority >= right.priority ? left : right;
            }
        }

        private record UserSignalContext(
                Map<Long, Double> seedWeights,
                Map<Long, PersonalSignalType> signalTypes,
                Set<Long> interactedPlaceIds
        ) {
            /** 익명 추천에 사용할 빈 가중치·신호 유형·제외 장소 집합을 생성. */
            private static UserSignalContext empty() {
                return new UserSignalContext(Map.of(), Map.of(), Set.of());
            }
        }

        private record PlaceDistance(MapPlace place, double distanceMeters) {
        }

        private record CandidateSelection(List<PlaceDistance> candidates, double appliedRadiusKm) {
        }

        private record IntermediateCandidate(
                MapPlace place,
                double distanceMeters,
                double geoScore,
                double personalScore,
                double rawQualityScore,
                double freshnessScore,
                PersonalSignalType dominantSignalType
        ) {
        }

        private record ScoredCandidate(
                MapPlace place,
                double distanceMeters,
                double qualityScore,
                double finalScore
        ) {
        }

        private final class PlaceAggregate {
            private long bookmarkCount;
            private long likeSum;
            private LocalDateTime latestCreatedAt;

            /** 사진 집계의 좋아요 합계와 최신 시각으로 현재 값을 설정. null 합계는 0으로 처리하고 기존 값은 덮어쓰기. */
            private void mergeImageAggregate(Long likeSum, LocalDateTime latestCreatedAt) {
                this.likeSum = likeSum == null ? 0L : likeSum;
                this.latestCreatedAt = latestCreatedAt;
            }
        }
    }
}
