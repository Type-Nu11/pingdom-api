package com.typenull.pingdom.privacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class PrivacyProcessingHistoryRepositoryPostgreSqlIntegrationTest {

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
    private PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    @BeforeEach
    void cleanDatabase() {
        privacyProcessingHistoryRepository.deleteAllInBatch();
    }

    @Test
    void filterQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
        PrivacyProcessingHistory older = history(10L, NOW.minusDays(10));
        PrivacyProcessingHistory newer = history(20L, NOW.minusDays(2));
        privacyProcessingHistoryRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(PrivacyProcessingHistory::getSubjectUserId)
                .containsExactly(20L, 10L);

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(PrivacyProcessingHistory::getSubjectUserId)
                .containsExactly(20L);

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(PrivacyProcessingHistory::getSubjectUserId)
                .containsExactly(10L);
    }

    @Test
    void 동일_Outbox_이벤트와_대상_사용자_조합은_한_번만_저장된다() {
        privacyProcessingHistoryRepository.saveAndFlush(history(10L, NOW, "outbox-event-1"));

        assertThatThrownBy(() -> privacyProcessingHistoryRepository.saveAndFlush(
                history(10L, NOW.plusSeconds(1), "outbox-event-1")
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Page<PrivacyProcessingHistory> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return privacyProcessingHistoryRepository.findByFilters(
                null,
                null,
                null,
                hasFrom,
                from,
                hasTo,
                to,
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ))
        );
    }

    private PrivacyProcessingHistory history(Long subjectUserId, LocalDateTime createdAt) {
        return history(subjectUserId, createdAt, null);
    }

    private PrivacyProcessingHistory history(Long subjectUserId, LocalDateTime createdAt, String outboxEventId) {
        return PrivacyProcessingHistory.builder()
                .subjectUserId(subjectUserId)
                .outboxEventId(outboxEventId)
                .actorUserId(100L)
                .actorType(PrivacyProcessingActorType.ADMIN)
                .action(PrivacyProcessingAction.EXPORT_REQUESTED)
                .details("관리자 개인정보 내보내기")
                .requestId("privacy-history-" + subjectUserId)
                .createdAt(createdAt)
                .build();
    }
}
