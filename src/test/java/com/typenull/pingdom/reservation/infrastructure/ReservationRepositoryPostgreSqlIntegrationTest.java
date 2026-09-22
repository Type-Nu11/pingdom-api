package com.typenull.pingdom.reservation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Tag("postgres-smoke")
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
class ReservationRepositoryPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS 컨테이너의 JDBC 주소·계정·PostgreSQL 드라이버를 Spring 데이터소스에 연결.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ReservationRepository reservationRepository;

    /**
     * 실제 PostgreSQL에서 선택 기간 필터를 null·비활성 플래그로 전달해 관리자 예약 조회가 빈 결과로 정상 실행되는지 검증.
     * nullable 기간 바인딩의 타입 추론 오류를 방지하는 회귀 테스트.
     */
    @Test
    void queriesWithoutOptionalPeriodFilters() {
        var result = reservationRepository.findAllForAdmin(
                ReservationStatus.PENDING,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );

        assertThat(result.getContent()).isEmpty();
    }
}
