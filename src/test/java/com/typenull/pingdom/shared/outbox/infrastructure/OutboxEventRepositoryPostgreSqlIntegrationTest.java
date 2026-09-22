package com.typenull.pingdom.shared.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class OutboxEventRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS 컨테이너의 JDBC 주소·계정·드라이버를 Spring 데이터소스에 등록한다.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    /**
     * 조회 테스트 전에 Outbox 이벤트를 일괄 삭제해 생성 시각 정렬과 기간 결과를 격리한다.
     */
    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAllInBatch();
    }

    /**
     * 실패 이벤트 2건에 대해 기간 없음·시작만·종료만 조건을 PostgreSQL에서 실행해 최신순/해당 기간 결과를 검증한다.
     */
    @Test
    void queriesOptionalOutboxPeriods() {
        OutboxEvent older = failedEvent("older", NOW.minusDays(10));
        OutboxEvent newer = failedEvent("newer", NOW.minusDays(2));
        outboxEventRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("newer", "older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("older");
    }

    /**
     * FAILED 상태와 주어진 생성 기간 플래그를 적용하고 생성 시각·이벤트 ID 내림차순 첫 20건을 조회한다.
     */
    private Page<OutboxEvent> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return outboxEventRepository.findByFilters(
                OutboxEventStatus.FAILED,
                null,
                null,
                null,
                hasFrom,
                from,
                hasTo,
                to,
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("eventId")
                ))
        );
    }

    /**
     * 주어진 집계와 생성 시각의 이메일 이벤트를 한 번 선점·실패시켜 최대 시도 1회의 FAILED 입력을 만든다.
     */
    private OutboxEvent failedEvent(String aggregateId, LocalDateTime createdAt) {
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:" + aggregateId,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                aggregateId,
                createdAt
        );
        event.claim(createdAt.plusMinutes(1));
        event.fail(createdAt.plusMinutes(2), 1, createdAt.plusMinutes(2), "provider unavailable");
        return event;
    }
}
