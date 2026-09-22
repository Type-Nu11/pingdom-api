package com.typenull.pingdom.place.infrastructure.persistence.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AdminPlaceRecommendationMetricRepositoryTest {

    /** 리플렉션으로 native SQL의 전환별 사전 집계·정렬 분기·동률 ID·count query 구조를 확인. DB에서의 SQL 실행은 검증 범위에서 제외. */
    @Test
    void preservesAggregatedMetricQuery() {
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

    /** 합계 native query 문자열에 기간·추천 버전·장소명 조건이 유지되는지 확인. */
    @Test
    void preservesMetricSumFilters() {
        Query query = getQuery("sumPeriodMetricCounts");

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).contains("created_at >= :cutoff");
        assertThat(query.value()).contains("(:recommendationVersion = '' OR recommendation_version = :recommendationVersion)");
        assertThat(query.value()).contains("p.place_name LIKE CONCAT('%', :keyword, '%')");
    }

    /** 이름으로 repository 메서드를 찾아 Query 어노테이션 존재를 확인한 뒤 SQL 계약을 읽음. */
    private Query getQuery(String methodName) {
        Method method = java.util.Arrays.stream(AdminPlaceRecommendationMetricRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("Method not found: " + methodName));

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}
