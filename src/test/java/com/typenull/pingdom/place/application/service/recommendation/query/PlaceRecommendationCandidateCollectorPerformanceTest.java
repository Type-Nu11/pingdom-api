package com.typenull.pingdom.place.application.service.recommendation.query;


import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class PlaceRecommendationCandidateCollectorPerformanceTest {

    @Autowired
    private PlaceRecommendationCandidateCollector placeRecommendationCandidateCollector;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @org.springframework.boot.test.mock.mockito.MockBean
    private S3Client s3Client;

    /**
     * 이전 북마크·스냅샷·장소 데이터를 지우고 조회 통계를 초기화합니다.
     */
    @BeforeEach
    void setUp() {
        mapBookmarkRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        statistics().clear();
    }

    /**
     * 개인 신호가 없으면 GEO·TREND 후보를 포함하고 PERSONAL 출처는 부여하지 않는지 확인합니다.
     */
    @Test
    void collectsAnonymousGeoAndTrend() {
        MapPlace geoPlace = createPlace("geo-place", 37.5000d, 127.0300d);
        MapPlace trendPlace = createPlace("trend-place", 37.5010d, 127.0310d);
        saveSnapshot(trendPlace.getId(), nowUtc().minusDays(1));

        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                37.5002d,
                127.0302d,
                UserSignalContext.empty()
        );

        assertThat(candidatePool)
                .extracting(candidate -> candidate.place().getId())
                .contains(geoPlace.getId(), trendPlace.getId());
        assertThat(candidatePool)
                .allSatisfy(candidate -> assertThat(candidate.sources()).doesNotContain(CandidateSource.PERSONAL));
        assertThat(candidatePool)
                .anySatisfy(candidate -> assertThat(candidate.sources()).contains(CandidateSource.GEO));
        assertThat(candidatePool)
                .anySatisfy(candidate -> assertThat(candidate.sources()).contains(CandidateSource.TREND));
    }

    /**
     * 북마크 시드와 주변 장소가 PERSONAL 출처 후보로 포함되는지 확인합니다.
     */
    @Test
    void expandsPersonalBookmarkSeed() {
        Long userId = 91L;
        MapPlace personalSeed = createPlace("personal-seed", 37.5000d, 127.0300d);
        MapPlace personalNeighbor = createPlace("personal-neighbor", 37.5007d, 127.0307d);
        createPlace("geo-place", 37.4980d, 127.0280d);
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(personalSeed.getId())
                .build());

        UserSignalContext signalContext = new UserSignalContext(
                java.util.Map.of(personalSeed.getId(), 1.0d),
                java.util.Map.of(personalSeed.getId(), PersonalSignalType.BOOKMARK),
                java.util.Set.of(personalSeed.getId())
        );

        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                37.5002d,
                127.0302d,
                signalContext
        );

        assertThat(candidatePool)
                .filteredOn(candidate -> candidate.sources().contains(CandidateSource.PERSONAL))
                .extracting(candidate -> candidate.place().getId())
                .contains(personalSeed.getId(), personalNeighbor.getId());
    }

    /**
     * 6일 전 갱신 스냅샷은 추세 후보에 포함하고 8일 전 것은 제외하는지 확인합니다.
     */
    @Test
    void limitsTrendSnapshotAge() {
        MapPlace freshTrendPlace = createPlace("fresh-trend", 37.5100d, 127.0400d);
        MapPlace staleTrendPlace = createPlace("stale-trend", 37.5200d, 127.0500d);
        saveSnapshot(freshTrendPlace.getId(), nowUtc().minusDays(6));
        saveSnapshot(staleTrendPlace.getId(), nowUtc().minusDays(8));

        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                37.5002d,
                127.0302d,
                UserSignalContext.empty()
        );

        assertThat(candidatePool)
                .filteredOn(candidate -> candidate.sources().contains(CandidateSource.TREND))
                .extracting(candidate -> candidate.place().getId())
                .contains(freshTrendPlace.getId())
                .doesNotContain(staleTrendPlace.getId());
    }

    /**
     * 후보 12개의 영속 맥락을 비운 후 수집 SQL이 6회를 넘지 않는지 확인하여 반복 영업 일정 조회를 방지합니다.
     */
    @Test
    void boundsCandidateCollectionQueries() {
        for (int index = 0; index < 12; index++) {
            MapPlace place = createPlace("trend-" + index, 37.5000d + (index * 0.001d), 127.0300d + (index * 0.001d));
            saveSnapshot(place.getId(), nowUtc().minusHours(index + 1L));
        }

        entityManager.flush();
        entityManager.clear();
        statistics().clear();

        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                37.5002d,
                127.0302d,
                UserSignalContext.empty()
        );

        long preparedStatementCount = statistics().getPrepareStatementCount();

        assertThat(candidatePool).isNotEmpty();
        assertThat(preparedStatementCount)
                .as("운영시간을 일괄 조회하므로 후보 수와 무관하게 쿼리 수가 일정해야 한다")
                .isLessThanOrEqualTo(6L);
    }

    /**
     * 위치와 사진 수가 있는 후보 수집용 장소를 저장합니다.
     */
    private MapPlace createPlace(String name, double latitude, double longitude) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(name + "-address")
                .latitude(latitude)
                .longitude(longitude)
                .userId(1L)
                .registrant("candidate-test")
                .photoCount(1L)
                .build());
    }

    /**
     * 추세 포함 여부를 조절할 갱신 시각의 추천 집계 스냅샷을 저장합니다.
     */
    private void saveSnapshot(Long placeId, LocalDateTime updatedAt) {
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(placeId)
                .photoCount(1L)
                .bookmarkCount(1L)
                .totalLikeCount(1L)
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(0L)
                .latestPostCreatedAt(updatedAt.minusHours(1))
                .updatedAt(updatedAt)
                .build());
    }

    /**
     * 운영 후보 수집과 같은 UTC 기준 현재 시각을 가져옵니다.
     */
    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 후보 수집 SQL 횟수를 확인할 Hibernate 통계를 가져옵니다.
     */
    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
