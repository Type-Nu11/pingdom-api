package com.typenull.pingdom.place.application.service.recommendation.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationFeatureLogServiceTest {

    @Mock
    private PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    private PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;

    @BeforeEach
    void setUp() {
        placeRecommendationFeatureLogService = new PlaceRecommendationFeatureLogService(
                placeRecommendationFeatureLogRepository
        );
    }

    @Test
    void findFeatureLogIdRequiresMatchingRecommendationVersion() {
        PlaceRecommendationFeatureLog featureLog = PlaceRecommendationFeatureLog.builder()
                .id(40L)
                .build();
        when(placeRecommendationFeatureLogRepository
                .findFirstByRequestIdAndUserIdAndPlaceIdAndRecommendationVersionOrderByIdAsc(
                        "request-id",
                        10L,
                        20L,
                        "place-rec-v2"
                )).thenReturn(Optional.of(featureLog));

        Long featureLogId = placeRecommendationFeatureLogService.findFeatureLogId(
                "request-id",
                10L,
                20L,
                "place-rec-v2"
        );

        assertThat(featureLogId).isEqualTo(40L);
    }

    @Test
    void findFeatureLogIdSkipsLookupWhenRequestIdIsMissing() {
        Long nullRequestFeatureLogId = placeRecommendationFeatureLogService.findFeatureLogId(
                null,
                10L,
                20L,
                "place-rec-v2"
        );
        Long blankRequestFeatureLogId = placeRecommendationFeatureLogService.findFeatureLogId(
                " ",
                10L,
                20L,
                "place-rec-v2"
        );

        assertThat(nullRequestFeatureLogId).isNull();
        assertThat(blankRequestFeatureLogId).isNull();
        verifyNoInteractions(placeRecommendationFeatureLogRepository);
    }

    @Test
    void recordShownCandidatesPersistsBoostContribution() {
        var record = new PlaceRecommendationFeatureRecord(
                20L, PlaceRecommendationCandidateSource.FALLBACK, 1, 100L,
                0.1d, 0.2d, 0.3d, 0.1d, 0.1d, 0.1d, 0.1d, 0.0d, 0.0d,
                0.0d, 0.0d, 0.08d, 0.48d);

        placeRecommendationFeatureLogService.recordShownCandidates(
                "request-id", 10L, "place-rec-v2", RecommendationStage.EXPERIMENTAL, List.of(record));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlaceRecommendationFeatureLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeRecommendationFeatureLogRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getBoostScore()).isEqualTo(0.08d);
    }
}
