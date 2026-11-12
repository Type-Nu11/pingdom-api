package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.typenull.pingdom.shared.observability.RecommendationMetrics.ExposureRejectionReason;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecommendationMetricsTest {

    @Test
    void distinguishesConfirmedSubmissionDropsFromUncertainPersistenceFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RecommendationMetrics metrics = new RecommendationMetrics(registry);
            metrics.recordExposureReceived(2);
            metrics.recordExposureSubmitted(2);
            metrics.recordExposureStarted(2, 15_000L);
            metrics.recordExposurePersisted(2, 20_000L);
            metrics.recordExposureRejected(3, ExposureRejectionReason.CAPACITY);
            metrics.recordExposureSubmissionFailure(4);
            metrics.recordExposurePersistenceFailure(5, 30_000L);

            assertThat(registry.get("pingdom.recommendation.exposure.events")
                    .tag("stage", "persisted").counter().count()).isEqualTo(1d);
            assertThat(registry.get("pingdom.recommendation.exposure.items")
                    .tag("stage", "persisted").counter().count()).isEqualTo(2d);
            assertThat(registry.get("pingdom.recommendation.exposure.dropped")
                    .tag("reason", "capacity").counter().count()).isEqualTo(3d);
            assertThat(registry.find("pingdom.recommendation.exposure.dropped")
                    .tag("reason", "submission_failed").counter()).isNull();
            assertThat(registry.find("pingdom.recommendation.exposure.dropped")
                    .tag("reason", "persistence_failed").counter()).isNull();
            assertThat(registry.get("pingdom.recommendation.exposure.items")
                    .tag("stage", "persistence_failed").counter().count()).isEqualTo(5d);
            assertThat(registry.get("pingdom.recommendation.exposure.queue_wait")
                    .timer().totalTime(TimeUnit.NANOSECONDS)).isEqualTo(15_000d);
            assertThat(registry.get("pingdom.recommendation.exposure.processing")
                    .tag("result", "failure").timer().totalTime(TimeUnit.NANOSECONDS)).isEqualTo(30_000d);
            assertThat(registry.getMeters()).allSatisfy(meter ->
                    assertThat(meter.getId().getTags()).allSatisfy(tag ->
                            assertThat(tag.getKey()).isIn("stage", "reason", "result")));
        } finally {
            registry.close();
        }
    }

    @Test
    void recordsSnapshotCoverageSeparatelyFromRebuildPairs() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RecommendationMetrics metrics = new RecommendationMetrics(registry);

            metrics.recordSimilarityCoverage(true, 5L, 1L);
            metrics.recordSimilarityCoverage(false, 0L, 6L);

            assertThat(registry.get("pingdom.recommendation.similarity.pairs")
                    .tags("mode", "online", "result", "hit").counter().count()).isEqualTo(5d);
            assertThat(registry.get("pingdom.recommendation.similarity.pairs")
                    .tags("mode", "online", "result", "miss").counter().count()).isEqualTo(1d);
            assertThat(registry.get("pingdom.recommendation.similarity.pairs")
                    .tags("mode", "rebuild", "result", "hit").counter().count()).isZero();
            assertThat(registry.get("pingdom.recommendation.similarity.pairs")
                    .tags("mode", "rebuild", "result", "miss").counter().count()).isEqualTo(6d);
        } finally {
            registry.close();
        }
    }

    @Test
    void recordsTransferredRowsAndQueryTimeForEachReaction() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RecommendationMetrics metrics = new RecommendationMetrics(registry);

            metrics.recordSimilarityBookmarkQuery(true, 4L, 20_000L);
            metrics.recordSimilarityLikeQuery(true, 3L, 30_000L);

            assertThat(registry.get("pingdom.recommendation.similarity.reaction.rows")
                    .tags("mode", "online", "reaction", "bookmark").summary().totalAmount()).isEqualTo(4d);
            assertThat(registry.get("pingdom.recommendation.similarity.reaction.rows")
                    .tags("mode", "online", "reaction", "like").summary().totalAmount()).isEqualTo(3d);
            assertThat(registry.get("pingdom.recommendation.similarity.reaction.query")
                    .tags("mode", "online", "reaction", "bookmark").timer()
                    .totalTime(TimeUnit.NANOSECONDS)).isEqualTo(20_000d);
            assertThat(registry.get("pingdom.recommendation.similarity.reaction.query")
                    .tags("mode", "online", "reaction", "like").timer()
                    .totalTime(TimeUnit.NANOSECONDS)).isEqualTo(30_000d);
        } finally {
            registry.close();
        }
    }
}
