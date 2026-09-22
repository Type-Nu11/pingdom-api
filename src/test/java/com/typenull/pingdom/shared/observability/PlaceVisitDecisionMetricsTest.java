package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionMetricsTest {

    /**
     * 임시 휴업 방문 판단 조회를 운영 상태 태그로 1건 기록하고 place_id 태그는 없어 고카디널리티 지표가 생기지 않는지 검증.
     */
    @Test
    void recordsBoundedVisitDecisionTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlaceVisitDecisionMetrics metrics = new PlaceVisitDecisionMetrics(registry);

        metrics.recordViewed(PlaceOperatingStatus.TEMPORARILY_CLOSED);

        assertThat(registry.find("pingdom.place.visit_decision_views")
                .tag("operating_status", "TEMPORARILY_CLOSED")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.find("pingdom.place.visit_decision_views").counters())
                .allSatisfy(counter -> assertThat(counter.getId().getTag("place_id")).isNull());
    }
}
