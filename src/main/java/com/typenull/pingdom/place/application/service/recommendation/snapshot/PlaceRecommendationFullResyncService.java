package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService.SnapshotResyncResult;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Checkpoint;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Phase;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 동기 HTTP 완료 계약을 유지하되 전체 실행 동안 DB 트랜잭션을 붙잡지 않는다. */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationFullResyncService {
    private final PlaceRecommendationResyncChunkService chunks;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SnapshotResyncResult resyncAll() {
        UUID token = UUID.randomUUID();
        chunks.claim(token);
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Recommendation resync interrupted");
                }
                Checkpoint state = chunks.execute(token);
                if (state.phase() == Phase.DONE) {
                    var result = state.result();
                    return new SnapshotResyncResult(result.placeCount(), result.synchronizedSnapshotCount(),
                            result.deletedSnapshotCount(), result.synchronizedSimilaritySnapshotCount(),
                            result.deletedSimilaritySnapshotCount(), result.synchronizedVersionSnapshotCount(),
                            result.deletedVersionSnapshotCount());
                }
            }
        } catch (RuntimeException failure) {
            try {
                chunks.release(token);
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }
}
