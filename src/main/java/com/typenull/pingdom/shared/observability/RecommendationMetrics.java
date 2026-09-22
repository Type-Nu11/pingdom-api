package com.typenull.pingdom.shared.observability;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService.SnapshotResyncResult;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 추천 버전별 요청 수·결과 개수 분포와 snapshot 재동기화·kill switch fallback을 집계.
 * 버전별 summary를 캐시하므로 호출자는 버전과 사유에 임의 식별자를 넣지 않아야 함.
 */
@Component
public class RecommendationMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, DistributionSummary> resultCountSummaries = new ConcurrentHashMap<>();

    public RecommendationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String recommendationVersion, int recommendedCount) {
        String version = safeTag(recommendationVersion);
        Tags tags = Tags.of("recommendation_version", version);
        meterRegistry.counter("pingdom.recommendation.requests", tags).increment();
        resultCountSummaries.computeIfAbsent(version, key ->
                DistributionSummary.builder("pingdom.recommendation.result_count")
                        .description("Recommended place count per request")
                        .tags(tags)
                        .register(meterRegistry)
        ).record(recommendedCount);
    }

    /** 성공 실행을 한 번 기록하고 각 snapshot 처리 건수 중 양수만 누적. 최종 저장 개수의 gauge와는 별도. */
    public void recordSnapshotResyncSuccess(SnapshotResyncResult result) {
        meterRegistry.counter(
                "pingdom.recommendation.snapshot_resync",
                Tags.of("result", "success", "reason", "none")
        ).increment();

        recordSnapshotResyncItemCount("place", result.placeCount());
        recordSnapshotResyncItemCount("snapshot_synchronized", result.synchronizedSnapshotCount());
        recordSnapshotResyncItemCount("snapshot_deleted", result.deletedSnapshotCount());
        recordSnapshotResyncItemCount("similarity_snapshot_synchronized", result.synchronizedSimilaritySnapshotCount());
        recordSnapshotResyncItemCount("similarity_snapshot_deleted", result.deletedSimilaritySnapshotCount());
        recordSnapshotResyncItemCount("version_snapshot_synchronized", result.synchronizedVersionSnapshotCount());
        recordSnapshotResyncItemCount("version_snapshot_deleted", result.deletedVersionSnapshotCount());
    }

    public void recordSnapshotResyncFailure(Throwable exception) {
        meterRegistry.counter(
                "pingdom.recommendation.snapshot_resync",
                Tags.of(
                        "result", "failure",
                        "reason", exception == null ? "unknown" : exception.getClass().getSimpleName()
                )
        ).increment();
    }

    public void recordKillSwitchFallback(String fromVersion, String toVersion, String reason) {
        meterRegistry.counter(
                "pingdom.recommendation.kill_switch_fallback",
                Tags.of(
                        "from_version", safeTag(fromVersion),
                        "to_version", safeTag(toVersion),
                        "reason", safeTag(reason)
                )
        ).increment();
    }

    private void recordSnapshotResyncItemCount(String item, long count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter(
                "pingdom.recommendation.snapshot_resync.items",
                Tags.of("item", item)
        ).increment(count);
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
