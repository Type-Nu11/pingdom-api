package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class AdminAuditLogRepositoryPostgreSqlIntegrationTest {

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
    private AdminAuditLogRepository adminAuditLogRepository;

    @BeforeEach
    void cleanDatabase() {
        adminAuditLogRepository.deleteAllInBatch();
    }

    @Test
    void filterQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
        AdminAuditLog older = auditLog("older", NOW.minusDays(10));
        AdminAuditLog newer = auditLog("newer", NOW.minusDays(2));
        adminAuditLogRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(AdminAuditLog::getTargetId)
                .containsExactly("newer", "older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(AdminAuditLog::getTargetId)
                .containsExactly("newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(AdminAuditLog::getTargetId)
                .containsExactly("older");
    }

    private Page<AdminAuditLog> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return adminAuditLogRepository.findByFilters(
                null,
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

    private AdminAuditLog auditLog(String targetId, LocalDateTime createdAt) {
        return AdminAuditLog.builder()
                .actorUserId(100L)
                .actorUsername("auditAdmin")
                .action(AdminAuditAction.USER_BAN_APPLIED)
                .targetType(AdminAuditTargetType.USER)
                .targetId(targetId)
                .reason("반복 신고")
                .requestId("audit-" + targetId)
                .createdAt(createdAt)
                .build();
    }
}
