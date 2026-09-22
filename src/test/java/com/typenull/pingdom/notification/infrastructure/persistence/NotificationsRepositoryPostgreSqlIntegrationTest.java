package com.typenull.pingdom.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
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
import org.springframework.transaction.annotation.Transactional;
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
class NotificationsRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);
    private static final List<NotificationType> ADMIN_TYPES = List.of(
            NotificationType.ADMIN_REPORT_RECEIVED,
            NotificationType.ADMIN_REPORT_PROCESSED,
            NotificationType.ADMIN_DUPLICATE_PLACE_DETECTED,
            NotificationType.ADMIN_USER_SANCTION
    );

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * 관리자 알림 필터와 중복 무시 삽입을 검증할 PostgreSQL 컨테이너 접속 정보를 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private NotificationsRepository notificationsRepository;

    /**
     * 기간 필터와 멱등 삽입의 행 수를 정확히 확인하도록 기존 알림을 제거.
     */
    @BeforeEach
    void cleanDatabase() {
        notificationsRepository.deleteAllInBatch();
    }

    /**
     * 관리자 알림 조회의 선택적 시작·종료 조건과 최신순 결과를 PostgreSQL에서 검증.
     */
    @Test
    void filtersAdminNotificationsByOptionalPeriod() {
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

    /**
     * 같은 수신자·이벤트의 두 번째 삽입은 0건이고 다른 수신자 삽입은 허용되어 총 두 행이 저장되는지 검증.
     */
    @Test
    @Transactional
    void deduplicatesAdminNotificationsPerRecipient() {
        int firstInsert = insertAdminNotification(10L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");
        int duplicateInsert = insertAdminNotification(10L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");
        int otherRecipientInsert = insertAdminNotification(11L, "ADMIN_NOTIFICATION:REPORT_RECEIVED:1");

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(otherRecipientInsert).isEqualTo(1);
        assertThat(notificationsRepository.count()).isEqualTo(2);
    }

    /**
     * 사용자 10의 관리자 알림 유형에 선택적 기간 조건을 적용하고 최신 생성 시각·ID 순으로 조회.
     */
    private Page<Notifications> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return notificationsRepository.findByAdminFilters(
                10L,
                ADMIN_TYPES,
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
     * 수신자와 이벤트 키를 바꾸어 신고 접수 알림의 중복 무시 삽입 결과를 확인.
     */
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

    /**
     * 기간 조회에 필요한 제목과 생성 시각을 지정해 읽지 않은 관리자 신고 알림을 생성.
     */
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
