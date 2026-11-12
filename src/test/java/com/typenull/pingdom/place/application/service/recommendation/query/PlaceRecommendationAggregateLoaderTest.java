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
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository.GlobalCountsProjection;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void preservesScoresAndRankingForEquivalentSeparateAndCombinedGlobalTotals() {
        var scoring = new PlaceRecommendationScoringService(mock(PlaceRecommendationSimilarityService.class));
        List<PlaceDistance> candidates = List.of(
                new PlaceDistance(MapPlace.builder().id(1L).name("first").build(), Set.of(), 100d, null, null),
                new PlaceDistance(MapPlace.builder().id(2L).name("second").build(), Set.of(), 300d, null, null),
                new PlaceDistance(MapPlace.builder().id(3L).name("third").build(), Set.of(), 700d, null, null));
        Map<Long, PlaceAggregate> aggregates = Map.of(
                1L, aggregate(1L, 1, 10), 2L, aggregate(2L, 5, 30), 3L, aggregate(3L, 0, 0));
        var similarity = new PlaceRecommendationSimilarityService.SimilarityContext(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0L);
        var weights = new RankingWeights(.2d, 0d, .1d, .3d, .2d, 0d, .2d, 0d);
        Instant sameTime = Instant.parse("2026-11-12T00:00:00Z");

        for (long[] separate : List.of(new long[]{12L, 60L}, new long[]{0L, 0L},
                new long[]{8L, 0L}, new long[]{8L, 40L})) {
            when(snapshots.sumGlobalCounts()).thenReturn(counts(separate[0], separate[1]));
            var combined = loader.resolveGlobalCounts();
            var baselineContext = scoring.buildScoreContext(candidates, aggregates, separate[0], separate[1]);
            var combinedContext = scoring.buildScoreContext(candidates, aggregates,
                    combined.clickCount(), combined.exposureCount());
            baselineContext = new PlaceRecommendationScoringService.RecommendationScoreContext(
                    baselineContext.globalCtr(), baselineContext.globalConversionRate(),
                    baselineContext.globalAverageLikePerPhoto(), sameTime);
            combinedContext = new PlaceRecommendationScoringService.RecommendationScoreContext(
                    combinedContext.globalCtr(), combinedContext.globalConversionRate(),
                    combinedContext.globalAverageLikePerPhoto(), sameTime);
            assertThat(combinedContext).isEqualTo(baselineContext);
            var baseline = scoring.applyFinalScores(scoring.buildIntermediateCandidates(candidates, aggregates,
                    UserSignalContext.empty(), Map.of(), Map.of(), separate[1], 0d, 5d,
                    similarity, baselineContext), weights);
            var current = scoring.applyFinalScores(scoring.buildIntermediateCandidates(candidates, aggregates,
                    UserSignalContext.empty(), Map.of(), Map.of(), combined.exposureCount(), 0d, 5d,
                    similarity, combinedContext), weights);

            assertThat(current).isEqualTo(baseline)
                    .allSatisfy(candidate -> assertThat(Double.isFinite(candidate.finalScore())).isTrue());
            Comparator<ScoredCandidate> ranking = Comparator.comparingDouble(ScoredCandidate::finalScore)
                    .reversed().thenComparing(candidate -> candidate.place().getId());
            assertThat(current.stream().sorted(ranking).map(candidate -> candidate.place().getId()).toList())
                    .isEqualTo(baseline.stream().sorted(ranking).map(candidate -> candidate.place().getId()).toList());
        }
        verifyNoInteractions(clicks, exposures);
    }

    private PlaceAggregate aggregate(long placeId, long clickCount, long exposureCount) {
        return PlaceAggregate.fromSnapshot(PlaceRecommendationSnapshot.builder().placeId(placeId)
                .photoCount(placeId * 2).bookmarkCount(placeId).totalLikeCount(placeId * 3)
                .clickCount(clickCount).exposureCount(exposureCount).bookmarkConversionCount(placeId)
                .latestPostCreatedAt(LocalDateTime.of(2026, 11, 11, 0, 0)).build());
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
