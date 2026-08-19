package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import java.time.LocalDateTime;
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
class AdminAdRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 12, 0);

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
    private AdminAdRepository adminAdRepository;

    @BeforeEach
    void cleanDatabase() {
        adminAdRepository.deleteAllInBatch();
    }

    @Test
    void listQuerySupportsAbsentAndPartialOptionalFiltersOnPostgreSql() {
        adminAdRepository.saveAndFlush(ad("종료된 광고", NOW.minusDays(10), NOW.minusDays(3)));
        adminAdRepository.saveAndFlush(ad("진행 중인 광고", NOW.minusDays(2), NOW.plusDays(3)));

        assertThat(find(false, null, false, null, false, null, false, false, false, false).getContent())
                .extracting(AdminAd::getTitle)
                .containsExactly("진행 중인 광고", "종료된 광고");

        assertThat(find(false, null, true, NOW.minusDays(5), false, null, false, false, false, false).getContent())
                .extracting(AdminAd::getTitle)
                .containsExactly("진행 중인 광고");

        assertThat(find(false, null, false, null, true, NOW.minusDays(5), false, false, false, false).getContent())
                .extracting(AdminAd::getTitle)
                .containsExactly("종료된 광고");

        assertThat(find(false, null, false, null, false, null, true, false, true, false).getContent())
                .extracting(AdminAd::getTitle)
                .containsExactly("진행 중인 광고");
    }

    private Page<AdminAd> find(
            boolean hasKeyword,
            String keyword,
            boolean hasStartedFrom,
            LocalDateTime startedFrom,
            boolean hasStartedTo,
            LocalDateTime startedTo,
            boolean hasDisplayStatus,
            boolean scheduled,
            boolean active,
            boolean expired
    ) {
        return adminAdRepository.findAdminAds(
                hasKeyword, keyword,
                hasStartedFrom, startedFrom,
                hasStartedTo, startedTo,
                hasDisplayStatus, scheduled, active, expired,
                NOW,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
    }

    private AdminAd ad(String title, LocalDateTime startAt, LocalDateTime endAt) {
        return AdminAd.builder()
                .title(title)
                .imageUrl("https://cdn.pingdom.com/banner/" + title + ".png")
                .redirectUrl("https://pingdom.com/events/" + title)
                .startAt(startAt)
                .endAt(endAt)
                .createdAt(startAt)
                .build();
    }
}
