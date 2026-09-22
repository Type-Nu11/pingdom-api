package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
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
class AdminAuditLogRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * 감사 로그의 선택적 날짜 조건을 PostgreSQL에서 검증하도록 컨테이너 접속 정보를 등록한다.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    /**
     * 기간 조건별 조회 결과를 독립적으로 검증하도록 감사 로그 테이블을 비운다.
     */
    @BeforeEach
    void cleanDatabase() {
        adminAuditLogRepository.deleteAllInBatch();
    }

    /**
     * 기간 조건 없음·시작만 지정·종료만 지정한 감사 로그 조회가 기대 대상 ID와 최신순 결과를 반환하는지 검증한다.
     */
    @Test
    void filtersAuditLogsByOptionalPeriod() {
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

    /**
     * 다른 조건을 비운 채 선택적 생성 기간으로 감사 로그의 첫 페이지를 생성 시각·ID 내림차순 조회한다.
     */
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

    /**
     * 기간별 결과를 구분하도록 대상 ID와 생성 시각을 지정한 사용자 정지 감사 로그를 만든다.
     */
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
