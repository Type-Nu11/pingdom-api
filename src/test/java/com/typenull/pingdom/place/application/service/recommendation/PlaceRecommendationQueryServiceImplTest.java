package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationExposureService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationGraphAffinityService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationQueryServiceImpl;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceRecommendationQueryServiceImplTest {

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @Mock
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

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
                placeRecommendationSnapshotRepository
        );

        placeRecommendationQueryService = new PlaceRecommendationQueryServiceImpl(
                mapPlaceRepository,
                mapBookmarkRepository,
                mapImageRepository,
                placeRecommendationSnapshotRepository,
                placeRecommendationClickService,
                placeRecommendationExposureService,
                placeGrowthService,
                placeRecommendationGraphAffinityService,
                placeRecommendationSimilarityService,
                placeRecommendationPolicyService,
                placeRecommendationFeatureLogService,
                placeRecommendationUserSignalLoader,
                placeRecommendationCandidateCollector
        );

        when(mapImageLikeRepository.findPlaceIdsByUserId(anyLong())).thenReturn(List.of());
        when(mapImageRepository.findPlaceIdsByUserId(anyLong())).thenReturn(List.of());
        when(placeRecommendationSnapshotRepository.findByUpdatedAtGreaterThanEqual(any(), any()))
                .thenReturn(Page.empty());
    }

    @Test
    void loadPersonalCandidates는_null_좌표_seed를_개인화_후보에서_제외한다() {
        Long userId = 7L;
        MapPlace invalidSeed = createPlace(101L, "null-seed", null, 128.1070d);
        MapPlace validSeed = createPlace(102L, "valid-seed", 35.1800d, 128.1070d);
        MapPlace expandedCandidate = createPlace(103L, "expanded", 35.1810d, 128.1080d);

        when(mapBookmarkRepository.findPlaceIdsByUserId(userId)).thenReturn(List.of(invalidSeed.getId(), validSeed.getId()));
        when(mapPlaceRepository.findAllById(any())).thenReturn(List.of(invalidSeed, validSeed));
        when(mapPlaceRepository.findRecommendationCandidatesInBoundingBox(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
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
