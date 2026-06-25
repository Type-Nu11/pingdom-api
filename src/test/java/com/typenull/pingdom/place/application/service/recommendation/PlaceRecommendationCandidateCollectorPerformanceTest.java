package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void setUp() {
        mapBookmarkRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        statistics().clear();
    }

    @Test
    void 비로그인_후보_수집은_PERSONAL_후보_없이_GEO와_TREND만_포함한다() {
        MapPlace geoPlace = createPlace("geo-place", 37.5000d, 127.0300d);
        MapPlace trendPlace = createPlace("trend-place", 37.5010d, 127.0310d);
        saveSnapshot(trendPlace.getId(), LocalDateTime.now().minusDays(1));

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

    @Test
    void 로그인_후보_수집은_북마크_seed를_기반으로_PERSONAL_후보를_포함한다() {
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

    @Test
    void trend_후보_수집은_최근_7일_이내_snapshot만_사용한다() {
        MapPlace freshTrendPlace = createPlace("fresh-trend", 37.5100d, 127.0400d);
        MapPlace staleTrendPlace = createPlace("stale-trend", 37.5200d, 127.0500d);
        saveSnapshot(freshTrendPlace.getId(), LocalDateTime.now().minusDays(6));
        saveSnapshot(staleTrendPlace.getId(), LocalDateTime.now().minusDays(8));

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

    @Test
    void trend_후보_다건_조회시_쿼리_수가_과도하게_증가하지_않는다() {
        for (int index = 0; index < 12; index++) {
            MapPlace place = createPlace("trend-" + index, 37.5000d + (index * 0.001d), 127.0300d + (index * 0.001d));
            saveSnapshot(place.getId(), LocalDateTime.now().minusHours(index + 1L));
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
        assertThat(preparedStatementCount).isLessThanOrEqualTo(4L);
    }

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

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
