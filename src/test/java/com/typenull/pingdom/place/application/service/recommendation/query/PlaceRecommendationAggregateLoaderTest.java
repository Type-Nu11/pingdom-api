package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository.GlobalCountsProjection;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationAggregateLoaderTest {

    @Mock private MapBookmarkRepository mapBookmarkRepository;
    @Mock private MapImageRepository mapImageRepository;
    @Mock private PlaceRecommendationSnapshotRepository snapshots;
    @Mock private PlaceRecommendationClickService clicks;
    @Mock private PlaceRecommendationExposureService exposures;
    @InjectMocks private PlaceRecommendationAggregateLoader loader;

    @Test
    void readsBothSnapshotTotalsWithOneRepositoryCall() {
        when(snapshots.sumGlobalCounts()).thenReturn(counts(12L, 60L));

        var result = loader.resolveGlobalCounts();

        assertThat(result).isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(12L, 60L));
        verify(snapshots).sumGlobalCounts();
        verifyNoInteractions(clicks, exposures);
    }

    @Test
    void preservesActualZeroWithoutFallingBackToSource() {
        when(snapshots.sumGlobalCounts()).thenReturn(counts(0L, 0L));

        var result = loader.resolveGlobalCounts();

        assertThat(result).isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(0L, 0L));
        var scoring = new PlaceRecommendationScoringService(mock(PlaceRecommendationSimilarityService.class));
        assertThat(scoring.buildScoreContext(List.of(), Map.of(), result.clickCount(), result.exposureCount())
                .globalCtr()).isZero();
        verifyNoInteractions(clicks, exposures);
    }

    @Test
    void fallsBackToBothSourcesWhenSnapshotSumsAreNull() {
        when(snapshots.sumGlobalCounts()).thenReturn(counts(null, null));
        when(clicks.countAllClicks()).thenReturn(8L);
        when(exposures.countAllExposures()).thenReturn(40L);

        assertThat(loader.resolveGlobalCounts())
                .isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(8L, 40L));

        verify(clicks).countAllClicks();
        verify(exposures).countAllExposures();
    }

    @Test
    void fallsBackOnlyForMissingClickTotal() {
        when(snapshots.sumGlobalCounts()).thenReturn(counts(null, 0L));
        when(clicks.countAllClicks()).thenReturn(8L);

        assertThat(loader.resolveGlobalCounts())
                .isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(8L, 0L));

        verifyNoInteractions(exposures);
    }

    @Test
    void fallsBackOnlyForMissingExposureTotal() {
        when(snapshots.sumGlobalCounts()).thenReturn(counts(0L, null));
        when(exposures.countAllExposures()).thenReturn(40L);

        assertThat(loader.resolveGlobalCounts())
                .isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(0L, 40L));

        verifyNoInteractions(clicks);
    }

    @Test
    void handlesAnAbsentProjectionWithTheExistingSourceFallback() {
        when(snapshots.sumGlobalCounts()).thenReturn(null);
        when(clicks.countAllClicks()).thenReturn(8L);
        when(exposures.countAllExposures()).thenReturn(40L);

        assertThat(loader.resolveGlobalCounts())
                .isEqualTo(new PlaceRecommendationAggregateLoader.GlobalCounts(8L, 40L));
    }

    @Test
    void propagatesSnapshotQueryFailureWithoutMaskingItWithSourceCounts() {
        var failure = new IllegalStateException("snapshot query failed");
        when(snapshots.sumGlobalCounts()).thenThrow(failure);

        assertThatThrownBy(loader::resolveGlobalCounts).isSameAs(failure);
        verifyNoInteractions(clicks, exposures);
    }

    private GlobalCountsProjection counts(Long clickCount, Long exposureCount) {
        return new GlobalCountsProjection() {
            @Override
            public Long getClickCount() { return clickCount; }

            @Override
            public Long getExposureCount() { return exposureCount; }
        };
    }
}
