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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    void filterQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
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
