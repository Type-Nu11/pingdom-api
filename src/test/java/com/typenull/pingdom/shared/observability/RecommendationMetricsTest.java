package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecommendationMetricsTest {

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
