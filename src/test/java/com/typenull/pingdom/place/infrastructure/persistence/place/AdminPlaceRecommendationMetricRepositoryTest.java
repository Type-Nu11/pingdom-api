package com.typenull.pingdom.place.infrastructure.persistence.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AdminPlaceRecommendationMetricRepositoryTest {

    @Test
    void periodMetricPageQueryMaintainsPreAggregatedNativeStructure() {
        Query query = getQuery("findPeriodRecommendationMetricPage");

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("FROM map_place p");
        assertThat(query.value()).contains("FROM place_recommendation_exposure");
        assertThat(query.value()).contains("FROM place_recommendation_click");
        assertThat(query.value()).contains("FROM place_recommendation_conversion");
        assertThat(query.value()).contains("GROUP BY place_id");
        assertThat(query.value()).contains("CASE WHEN :sortBy = 'CLICK'");
        assertThat(query.value()).contains("CASE WHEN :sortBy = 'TOTAL_CONVERSION'");
        assertThat(query.value()).contains("p.map_place_id ASC");
        assertThat(query.countQuery()).contains("SELECT COUNT(*)");
    }

    @Test
    void periodMetricSumQueryKeepsRecommendationVersionAndCutoffFilters() {
        Query query = getQuery("sumPeriodMetricCounts");

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("created_at >= :cutoff");
        assertThat(query.value()).contains("(:recommendationVersion = '' OR recommendation_version = :recommendationVersion)");
        assertThat(query.value()).contains("p.place_name LIKE CONCAT('%', :keyword, '%')");
    }

    private Query getQuery(String methodName) {
        Method method = java.util.Arrays.stream(AdminPlaceRecommendationMetricRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}
