package com.typenull.pingdom.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionMetricsTest {

    @Test
    void recordsViewsByOperatingStatusWithoutPlaceIdTag() {
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
