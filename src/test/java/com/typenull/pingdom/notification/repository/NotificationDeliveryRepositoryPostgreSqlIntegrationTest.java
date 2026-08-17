package com.typenull.pingdom.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
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
class NotificationDeliveryRepositoryPostgreSqlIntegrationTest {

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
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @BeforeEach
    void cleanDatabase() {
        notificationDeliveryRepository.deleteAllInBatch();
    }

    @Test
    void filterQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
        NotificationDelivery older = delivery("event-older", NOW.minusDays(10));
        NotificationDelivery newer = delivery("event-newer", NOW.minusDays(2));
        notificationDeliveryRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-newer", "event-older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-older");
    }

    private Page<NotificationDelivery> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return notificationDeliveryRepository.findByFilters(
                null,
                null,
                NotificationDeliveryStatus.FAILED,
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

    private NotificationDelivery delivery(String outboxEventId, LocalDateTime createdAt) {
        NotificationDelivery delivery = NotificationDelivery.create(
                NotificationDeliveryChannel.FCM,
                10L,
                100L,
                "NEW_LIKE",
                outboxEventId,
                "MAP_IMAGE_LIKED",
                "recipient-hash-" + outboxEventId,
                createdAt
        );
        delivery.recordResult(
                NotificationDeliveryStatus.FAILED,
                10L,
                100L,
                "NEW_LIKE",
                "MAP_IMAGE_LIKED",
                null,
                null,
                "FCM_SEND_FAILED",
                "failed",
                false,
                1,
                createdAt
        );
        return delivery;
    }
}
