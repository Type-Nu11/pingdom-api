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

    /**
     * PostGIS 컨테이너의 JDBC 접속값과 PostgreSQL 드라이버를 Spring 테스트 데이터소스에 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    /**
     * 각 테스트 전에 개인정보 감사 이력을 일괄 삭제해 조회 순서와 중복 제약 검증을 격리.
     */
    @BeforeEach
    void cleanDatabase() {
        privacyProcessingHistoryRepository.deleteAllInBatch();
    }

    /**
     * PostgreSQL에 시각이 다른 이력 2건을 저장하고 기간 없음·시작만·종료만 조건의 내림차순 결과를 검증.
     * nullable 시각 바인딩과 각 적용 플래그가 올바르게 동작하는지 고정.
     */
    @Test
    void queriesOptionalPrivacyPeriods() {
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

    /**
     * 같은 Outbox 이벤트 ID·대상 사용자 조합을 두 번 저장하면 PostgreSQL 고유 제약으로 DataIntegrityViolationException이 발생하는지 검증.
     */
    @Test
    void rejectsDuplicatePrivacyOutboxSubject() {
        privacyProcessingHistoryRepository.saveAndFlush(history(10L, NOW, "outbox-event-1"));

        assertThatThrownBy(() -> privacyProcessingHistoryRepository.saveAndFlush(
                history(10L, NOW.plusSeconds(1), "outbox-event-1")
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 사용자·행위자·행위 필터를 생략하고 주어진 기간 적용 플래그와 시각으로 최신 이력 20건을 조회.
     */
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

    /** Outbox 연결이 없는 관리자 export 이력을 주어진 대상 사용자와 시각으로 생성. */
    private PrivacyProcessingHistory history(Long subjectUserId, LocalDateTime createdAt) {
        return history(subjectUserId, createdAt, null);
    }

    /** 대상 사용자·발생 시각·선택적 Outbox ID를 지정해 관리자 export 이력의 조회/고유 제약 입력을 생성. */
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
