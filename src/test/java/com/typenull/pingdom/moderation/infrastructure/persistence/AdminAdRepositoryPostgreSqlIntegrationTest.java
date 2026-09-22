package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import java.time.LocalDateTime;
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
class AdminAdRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * 광고 목록의 선택적 필터를 실제 PostgreSQL에서 실행하도록 PostGIS 컨테이너 접속 정보를 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AdminAdRepository adminAdRepository;

    /**
     * 광고 기간·노출 상태 필터 결과가 이전 행의 영향을 받지 않도록 테이블을 비움.
     */
    @BeforeEach
    void cleanDatabase() {
        adminAdRepository.deleteAllInBatch();
    }

    /**
     * 필터가 없거나 시작일 하한·상한만 지정한 경우와 진행 중 상태 필터의 광고 결과를 PostgreSQL에서 검증.
     */
    @Test
    void filtersAdsByOptionalCriteria() {
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

    /**
     * 선택적 키워드·시작 기간·노출 상태와 고정 현재 시각을 전달해 최신 생성 시각·ID 순으로 광고를 조회.
     */
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

    /**
     * 지정 제목과 시작·종료 시각으로 광고를 만들어 종료된 광고와 진행 중 광고를 구분.
     */
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
