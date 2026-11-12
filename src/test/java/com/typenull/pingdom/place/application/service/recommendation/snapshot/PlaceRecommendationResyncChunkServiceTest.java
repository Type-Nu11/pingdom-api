package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.*;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceRecommendationResyncChunkServiceTest {
    private final RecommendationResyncCheckpointRepository checkpoints = mock(RecommendationResyncCheckpointRepository.class);
    private final RecommendationResyncQueryRepository queries = mock(RecommendationResyncQueryRepository.class);
    private final PlaceRecommendationSnapshotResyncService snapshots = mock(PlaceRecommendationSnapshotResyncService.class);
    private final PlaceRecommendationVersionSnapshotService versions = mock(PlaceRecommendationVersionSnapshotService.class);
    private final PlaceRecommendationSimilarityService similarity = mock(PlaceRecommendationSimilarityService.class);
    private final PlaceRecommendationSnapshotWriteLock locks = mock(PlaceRecommendationSnapshotWriteLock.class);
    private final PlaceRecommendationResyncChunkService service =
            new PlaceRecommendationResyncChunkService(checkpoints, queries, snapshots, versions, similarity, locks);
    private final UUID token = UUID.randomUUID();

    @Test
    void advancesCountsAndCursorOnlyAfterCounterWrites() {
        var state = state(Phase.COUNTERS);
        var place = MapPlace.builder().id(10L).build();
        when(checkpoints.lock(token)).thenReturn(state);
        when(queries.placeIds(0, 100)).thenReturn(List.of(10L));
        when(locks.lockPlaces(List.of(10L))).thenReturn(List.of(place));
        when(snapshots.synchronizeSnapshots(eq(List.of(place)), any())).thenReturn(1L);
        when(versions.synchronizeVersions(eq(List.of(10L)), any()))
                .thenReturn(new PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult(2, 1));
        var result = service.execute(token);
        assertThat(result.left()).isEqualTo(10);
        assertThat(result.result().synchronizedSnapshotCount()).isEqualTo(1);
        assertThat(result.result().synchronizedVersionSnapshotCount()).isEqualTo(2);
        var order = inOrder(checkpoints, snapshots, versions);
        order.verify(checkpoints).lock(token);
        order.verify(snapshots).synchronizeSnapshots(any(), any());
        order.verify(versions).synchronizeVersions(any(), any());
        order.verify(checkpoints).save(token, result);
    }

    @Test
    void rejectsFencedExecutionBeforeTouchingSnapshots() {
        when(checkpoints.lock(token)).thenThrow(new IllegalStateException("fenced"));
        assertThatThrownBy(() -> service.execute(token)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(queries, snapshots, versions, similarity, locks);
    }

    @Test
    void locksCurrentEndpointsBeforeRecheckingStalePairs() {
        when(checkpoints.lock(token)).thenReturn(state(Phase.SIMILARITY_STALE));
        when(queries.existingPairs(0, 100)).thenReturn(List.of(new ExistingPair(7, 20, 10)));
        when(queries.deleteInvalidPairs(List.of(7L))).thenReturn(0);
        var result = service.execute(token);
        var order = inOrder(locks, queries);
        order.verify(queries).existingPairs(0, 100);
        order.verify(locks).lockPlaces(List.of(10L, 20L));
        order.verify(queries).deleteInvalidPairs(List.of(7L));
        assertThat(result.left()).isEqualTo(7);
        assertThat(result.result().deletedSimilaritySnapshotCount()).isZero();
    }

    private Checkpoint state(Phase phase) {
        return new Checkpoint(phase, 0, 0, 100, 100, 100, 100, 10, Counts.empty());
    }
}
