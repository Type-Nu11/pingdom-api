package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Checkpoint;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Counts;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Phase;
import org.junit.jupiter.api.Test;

class PlaceRecommendationFullResyncServiceTest {
    @Test
    void returnsPersistedTotalsAfterAllChunksComplete() {
        var chunks = mock(PlaceRecommendationResyncChunkService.class);
        when(chunks.execute(any())).thenReturn(state(Phase.COUNTERS), state(Phase.DONE));
        var result = new PlaceRecommendationFullResyncService(chunks).resyncAll();
        assertThat(result.synchronizedSimilaritySnapshotCount()).isEqualTo(3_000_000_000L);
        verify(chunks).claim(any());
        verify(chunks, times(2)).execute(any());
        verify(chunks, never()).release(any());
    }

    @Test
    void releasesOnlyItsClaimAndPreservesChunkFailure() {
        var chunks = mock(PlaceRecommendationResyncChunkService.class);
        var failure = new IllegalStateException("chunk failed");
        when(chunks.execute(any())).thenThrow(failure);
        doThrow(new IllegalStateException("release failed")).when(chunks).release(any());
        assertThatThrownBy(() -> new PlaceRecommendationFullResyncService(chunks).resyncAll()).isSameAs(failure);
        assertThat(failure.getSuppressed()).hasSize(1);
        verify(chunks).release(any());
    }

    private Checkpoint state(Phase phase) {
        return new Checkpoint(phase, 1000, 0, 1000, 1000, 0, 0, 0,
                new Counts(1000, 1000, 0, 3_000_000_000L, 0, 0, 0));
    }
}
