package com.typenull.pingdom.integration.observability;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.shared.web.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * health 공개 범위와 요청 ID 전달, Redis health 대역 변화에 따른 readiness 응답을 검증.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "management.endpoint.health.group.readiness.include=readinessState,db,redis"
})
@AutoConfigureMockMvc
@Import(ActuatorObservabilitySecurityTest.RedisReadinessHealthTestConfiguration.class)
class ActuatorObservabilitySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisReadinessHealthIndicator redisHealthIndicator;

    /**
     * 이전 장애 시나리오의 영향을 없애도록 대역 Redis health 상태를 정상으로 전환.
     */
    @BeforeEach
    void setUp() {
        redisHealthIndicator.markUp();
    }

    /**
     * 인증 없이 health 조회가 성공하고 상태 및 생성된 요청 ID 헤더를 제공하는지 확인.
     */
    @Test
    void publicHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, not(blankOrNullString())));
    }

    /**
     * 인증 없이 readiness 상태를 조회할 수 있는지 확인.
     */
    @Test
    void publicReadiness() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    /**
     * Redis health 대역을 DOWN으로 바꾸면 readiness가 503과 DOWN 상태를 반환하는지 확인. 실제 Redis 장애 상황은 검증 범위에서 제외.
     */
    @Test
    void redisDownReadiness() throws Exception {
        redisHealthIndicator.markDown();

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    /**
     * health와 달리 metrics 경로는 미인증 요청에 401을 반환하는지 확인.
     */
    @Test
    void metricsRequireToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 클라이언트가 지정한 요청 ID가 health 응답 헤더에도 동일하게 전달되는지 확인.
     */
    @Test
    void requestIdHeaderIsPropagated() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "client-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "client-request-1"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisReadinessHealthTestConfiguration {

        /**
         * 실제 Redis 대신 테스트에서 상태를 제어할 health indicator를 빈으로 등록.
         */
        @Bean
        RedisReadinessHealthIndicator redisHealthIndicator() {
            return new RedisReadinessHealthIndicator();
        }
    }

    static class RedisReadinessHealthIndicator implements HealthIndicator {

        private boolean available = true;

        /**
         * 테스트가 설정한 가용성 플래그를 UP 또는 DOWN health 결과로 변환.
         */
        @Override
        public Health health() {
            return available ? Health.up().build() : Health.down().build();
        }

        /**
         * 다음 readiness 조회에서 Redis가 정상인 상태를 구성.
         */
        void markUp() {
            available = true;
        }

        /**
         * 다음 readiness 조회에서 Redis가 비정상인 상태를 구성.
         */
        void markDown() {
            available = false;
        }
    }
}
