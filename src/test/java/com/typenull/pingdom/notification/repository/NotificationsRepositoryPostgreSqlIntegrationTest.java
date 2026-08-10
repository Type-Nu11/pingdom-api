package com.typenull.pingdom.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
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
import org.springframework.transaction.annotation.Transactional;
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
class NotificationsRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

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
    private NotificationsRepository notificationsRepository;

    @BeforeEach
    void cleanDatabase() {
        notificationsRepository.deleteAllInBatch();
    }

    @Test
    void adminFilterQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
        Notifications older = notification("older", NOW.minusDays(10));
        Notifications newer = notification("newer", NOW.minusDays(2));
        notificationsRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(Notifications::getTitle)
                .containsExactly("newer", "older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(Notifications::getTitle)
                .containsExactly("newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(Notifications::getTitle)
                .containsExactly("older");
    }

    @Test
    @Transactional
    void adminNotificationInsertIsIdempotentPerRecipientAndEvent() {
        int firstInsert = insertAdminNotification(10L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");
        int duplicateInsert = insertAdminNotification(10L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");
        int otherRecipientInsert = insertAdminNotification(11L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(otherRecipientInsert).isEqualTo(1);
        assertThat(notificationsRepository.count()).isEqualTo(2);
    }

    private Page<Notifications> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return notificationsRepository.findByAdminFilters(
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

    private int insertAdminNotification(Long userId, String eventKey) {
        return notificationsRepository.insertAdminNotificationIfAbsent(
                userId,
                NotificationType.ADMIN_REPORT_RECEIVED.name(),
                "신고 접수 알림",
                "신고 ID 1 접수가 게시글 ID 2에 등록되었습니다.",
                "report:1",
                eventKey,
                NOW
        );
    }

    private Notifications notification(String title, LocalDateTime createdAt) {
        return Notifications.builder()
                .userId(10L)
                .type(NotificationType.ADMIN_REPORT_RECEIVED)
                .title(title)
                .body("새로운 신고가 접수되었습니다.")
                .token("report:1")
                .isRead(false)
                .createdAt(createdAt)
                .build();
    }
}
