package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import com.typenull.pingdom.place.application.service.recommendation.policy.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.event.PlaceRecommendationExposureRecordRequestedEvent;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrustScoreRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import java.util.List;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceRecommendationQueryServiceImplTest {

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private MapPlaceRecommendationCandidateRepository mapPlaceRecommendationCandidateRepository;

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @Mock
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Mock
    private PlaceRecommendationTrustScoreRepository placeRecommendationTrustScoreRepository;

    @Mock
    private PlaceRecommendationClickService placeRecommendationClickService;

    @Mock
    private PlaceRecommendationExposureService placeRecommendationExposureService;

    @Mock
    private PlaceGrowthService placeGrowthService;

    @Mock
    private PlaceRecommendationGraphAffinityService placeRecommendationGraphAffinityService;

    @Mock
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Mock
    private PlaceRecommendationPolicyService placeRecommendationPolicyService;

    @Mock
    private PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;

    @Mock
    private RecommendationMetrics recommendationMetrics;

    @Mock
    private UserCurrentActivityIntentRepository userCurrentActivityIntentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlaceRecommendationCommerceSignalLoader placeRecommendationCommerceSignalLoader;

    private PlaceRecommendationQueryServiceImpl placeRecommendationQueryService;
    private PlaceRecommendationUserSignalLoader placeRecommendationUserSignalLoader;
    private PlaceRecommendationCandidateCollector placeRecommendationCandidateCollector;

    @BeforeEach
    void setUp() {
        placeRecommendationUserSignalLoader = new PlaceRecommendationUserSignalLoader(
                mapBookmarkRepository,
                mapImageLikeRepository,
                mapImageRepository
        );
        placeRecommendationCandidateCollector = new PlaceRecommendationCandidateCollector(
                mapPlaceRepository,
                mapPlaceRecommendationCandidateRepository,
                placeRecommendationSnapshotRepository,
                new PlaceOperatingHoursEvaluator(Clock.fixed(
                        Instant.parse("2026-07-21T12:00:00Z"),
                        ZoneOffset.UTC
                ))
        );
        PlaceRecommendationAggregateLoader placeRecommendationAggregateLoader = new PlaceRecommendationAggregateLoader(
                mapBookmarkRepository,
                mapImageRepository,
                placeRecommendationSnapshotRepository,
                placeRecommendationClickService,
                placeRecommendationExposureService
        );
        PlaceRecommendationScoringService placeRecommendationScoringService = new PlaceRecommendationScoringService(
                placeRecommendationSimilarityService
        );
        PlaceRecommendationTrustScoreLoader placeRecommendationTrustScoreLoader =
                new PlaceRecommendationTrustScoreLoader(placeRecommendationTrustScoreRepository);
        CurrentActivityIntentRankingService currentActivityIntentRankingService =
                new CurrentActivityIntentRankingService(
                        userCurrentActivityIntentRepository,
                        mapPlaceRecommendationCandidateRepository,
                        java.time.Clock.systemUTC()
                );
        KCultureInterestRankingService kCultureInterestRankingService = new KCultureInterestRankingService(
                userRepository,
                mapPlaceRecommendationCandidateRepository
        );
        PlaceRecommendationPortfolioService placeRecommendationPortfolioService = new PlaceRecommendationPortfolioService(
                placeRecommendationSimilarityService
        );

        placeRecommendationQueryService = new PlaceRecommendationQueryServiceImpl(
                mapPlaceRepository,
                placeGrowthService,
                placeRecommendationGraphAffinityService,
                placeRecommendationSimilarityService,
                placeRecommendationPolicyService,
                placeRecommendationFeatureLogService,
                placeRecommendationUserSignalLoader,
                placeRecommendationCandidateCollector,
                placeRecommendationAggregateLoader,
                placeRecommendationTrustScoreLoader,
                placeRecommendationScoringService,
                kCultureInterestRankingService,
                currentActivityIntentRankingService,
                placeRecommendationCommerceSignalLoader,
                new PlaceRecommendationCommerceRankingService(),
                placeRecommendationPortfolioService,
                recommendationMetrics,
                eventPublisher
        );

        when(mapImageLikeRepository.findPlaceIdsByUserId(anyLong())).thenReturn(List.of());
        when(mapImageRepository.findPlaceIdsByUserId(anyLong())).thenReturn(List.of());
        when(placeRecommendationSnapshotRepository.findByUpdatedAtGreaterThanEqual(any(), any()))
                .thenReturn(Page.empty());
        when(placeRecommendationTrustScoreRepository.findTrustScoresByPlaceIds(any())).thenReturn(List.of());
        when(placeRecommendationCommerceSignalLoader.load(any())).thenReturn(java.util.Map.of());
    }

    @Test
    void loadPersonalCandidates는_null_좌표_seed를_개인화_후보에서_제외한다() {
        Long userId = 7L;
        MapPlace invalidSeed = createPlace(101L, "null-seed", null, 128.1070d);
        MapPlace validSeed = createPlace(102L, "valid-seed", 35.1800d, 128.1070d);
        MapPlace expandedCandidate = createPlace(103L, "expanded", 35.1810d, 128.1080d);

        when(mapBookmarkRepository.findPlaceIdsByUserId(userId)).thenReturn(List.of(invalidSeed.getId(), validSeed.getId()));
        when(mapPlaceRepository.findAllById(any())).thenReturn(List.of(invalidSeed, validSeed));
        when(mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInBoundingBox(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                any(PlaceOperatingStatus.class),
                any(PlaceDiscoveryStatus.class),
                any(Pageable.class)
        )).thenReturn(List.of(expandedCandidate));

        UserSignalContext signalContext = placeRecommendationUserSignalLoader.loadUserSignals(userId);

        List<CandidatePlace> candidatePool = placeRecommendationCandidateCollector.loadCandidatePool(
                35.1800d,
                128.1070d,
                signalContext
        );

        List<MapPlace> personalCandidates = candidatePool.stream()
                .filter(candidate -> candidate.sources().contains(CandidateSource.PERSONAL))
                .map(CandidatePlace::place)
                .toList();

        assertThat(personalCandidates)
                .extracting(MapPlace::getId)
                .containsExactlyInAnyOrder(validSeed.getId(), expandedCandidate.getId());
    }

    @Test
    void recommendPlaces는_노출_로그_기록_이벤트를_발행한다() {
        Long userId = 7L;
        MapPlace candidate = createPlace(201L, "candidate", 35.1800d, 128.1070d);

        when(mapBookmarkRepository.findPlaceIdsByUserId(userId)).thenReturn(List.of());
        when(mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInBoundingBox(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                any(PlaceOperatingStatus.class),
                any(PlaceDiscoveryStatus.class),
                any(Pageable.class)
        )).thenReturn(List.of(candidate));
        when(placeRecommendationPolicyService.resolve(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PlaceRecommendationPolicyService.ResolvedRecommendationPolicy(
                        "place-rec-v1",
                        RecommendationStage.STABLE,
                        false,
                        4,
                        0.75d,
                        0.10d,
                        0.15d,
                        new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                        new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d, 0.0d),
                        new RankingWeights(0.48d, 0.0d, 0.16d, 0.10d, 0.08d, 0.12d, 0.09d, 0.0d),
                        "place-rec-v1",
                        null
                ));

        placeRecommendationQueryService.recommendPlaces(userId, 35.1800d, 128.1070d, 1, 5.0d, null);

        verify(eventPublisher, timeout(1000)).publishEvent(argThat((Object event) -> {
            if (!(event instanceof PlaceRecommendationExposureRecordRequestedEvent exposureEvent)) {
                return false;
            }
            return exposureEvent.userId().equals(userId)
                    && exposureEvent.placeIds().equals(List.of(candidate.getId()))
                    && exposureEvent.recommendationVersion().equals("place-rec-v1");
        }));
    }

    @Test
    void recommendPlaces는_현재_영업중인_후보를_영업외_후보보다_우선한다() {
        MapPlace closedCandidate = createPlace(301L, "closed", 35.1800d, 128.1070d);
        closedCandidate.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0))
        ), List.of());
        MapPlace openCandidate = createPlace(302L, "open", 35.1810d, 128.1080d);
        openCandidate.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(14, 0))
        ), List.of());

        when(mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInBoundingBox(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                any(PlaceOperatingStatus.class),
                any(PlaceDiscoveryStatus.class),
                any(Pageable.class)
        )).thenReturn(List.of(closedCandidate, openCandidate));
        when(placeRecommendationPolicyService.resolve(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(stablePolicy(true));

        var response = placeRecommendationQueryService.recommendPlaces(
                null,
                35.1800d,
                128.1070d,
                2,
                5.0d,
                null
        );

        assertThat(response.places())
                .extracting(place -> place.id())
                .containsExactly(openCandidate.getId(), closedCandidate.getId());
        assertThat(response.places())
                .extracting(place -> place.currentlyOperating())
                .containsExactly(true, false);
        verify(placeRecommendationFeatureLogService).recordShownCandidates(
                any(),
                any(),
                any(),
                any(),
                argThat(records -> records.stream().map(record -> record.placeId()).toList()
                        .equals(List.of(openCandidate.getId(), closedCandidate.getId())))
        );
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof PlaceRecommendationExposureRecordRequestedEvent exposureEvent
                        && exposureEvent.placeIds().equals(List.of(openCandidate.getId(), closedCandidate.getId()))
        ));
    }

    @Test
    void recommendPlaces는_limit이_부족해도_영업중_후보를_먼저_선택한다() {
        MapPlace closedCandidate = createPlace(401L, "high-score-closed", 35.1800d, 128.1070d);
        closedCandidate.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0))
        ), List.of());
        MapPlace openCandidate = createPlace(402L, "open", 35.1900d, 128.1170d);
        openCandidate.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(14, 0))
        ), List.of());

        when(mapPlaceRecommendationCandidateRepository.findRecommendationCandidatesInBoundingBox(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(PlaceOperatingStatus.class), any(PlaceDiscoveryStatus.class), any(Pageable.class)
        )).thenReturn(List.of(closedCandidate, openCandidate));
        when(placeRecommendationPolicyService.resolve(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(stablePolicy(false));

        var response = placeRecommendationQueryService.recommendPlaces(
                null, 35.1800d, 128.1070d, 1, 5.0d, null
        );

        assertThat(response.places())
                .extracting(place -> place.id())
                .containsExactly(openCandidate.getId());
    }

    private PlaceRecommendationPolicyService.ResolvedRecommendationPolicy stablePolicy(boolean featureLoggingEnabled) {
        return new PlaceRecommendationPolicyService.ResolvedRecommendationPolicy(
                "place-rec-v1",
                RecommendationStage.STABLE,
                featureLoggingEnabled,
                4,
                0.75d,
                0.10d,
                0.15d,
                new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d, 0.0d),
                new RankingWeights(0.48d, 0.0d, 0.16d, 0.10d, 0.08d, 0.12d, 0.09d, 0.0d),
                "place-rec-v1",
                null
        );
    }

    private MapPlace createPlace(Long id, String name, Double latitude, Double longitude) {
        return MapPlace.builder()
                .id(id)
                .name(name)
                .address(name + "-address")
                .latitude(latitude)
                .longitude(longitude)
                .userId(1L)
                .registrant("tester")
                .photoCount(1L)
                .build();
    }
}
