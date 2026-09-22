package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VisitVerificationPolicyResolverTest {

    /**
     * 장소 2에 지정한 반경 100m가 전역 기본값 500m보다 우선하는지 검증.
     * 별도 설정이 없는 장소 3에는 기본 반경을 적용하며,
     * 반경을 재정의한 장소에서도 기본 체류 시간 30초는 유지되어야 함.
     */
    @Test
    void preferPlaceRadius() {
        VisitVerificationProperties properties = new VisitVerificationProperties(500.0, Map.of(2L, 100.0), 20.0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofDays(30), 1000.0,
                Duration.ofSeconds(30));
        VisitVerificationPolicyResolver resolver = new VisitVerificationPolicyResolver(properties);

        assertThat(resolver.resolve(2L).requiredRadiusMeters()).isEqualTo(100.0);
        assertThat(resolver.resolve(3L).requiredRadiusMeters()).isEqualTo(500.0);
        assertThat(resolver.resolve(2L).requiredDwellDuration()).isEqualTo(Duration.ofSeconds(30));
    }
}
