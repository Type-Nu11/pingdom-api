package com.typenull.pingdom.integration.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 빈 PostGIS DB에 Flyway를 적용한 Spring 컨텍스트가 Hibernate validate를 통과하는지 검증한다.
 */
@Tag("postgres-integration")
@Tag("migration")
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class FlywayHibernateSchemaValidationIntegrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    static {
        postgres.start();
        ensureRequiredExtensions();
    }

    /**
     * 직접 시작한 PostGIS 컨테이너를 Spring 데이터소스로 연결한다.
     */
    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * 클래스 종료 시 테스트 컨테이너를 정리한다.
     */
    @AfterAll
    static void stopPostgres() {
        postgres.stop();
    }

    /**
     * 본문 assertion 대신 Spring 컨텍스트 초기화가 완료됨을 검증한다. Flyway 적용과 Hibernate validate 실패는 테스트 진입 전에 드러난다.
     */
    @Test
    void migratedSchemaMatchesHibernate() {
    }

    /**
     * Flyway 적용 전에 postgis와 pg_trgm 확장을 준비하고 실패 시 컨텍스트 기동을 중단한다.
     */
    private static void ensureRequiredExtensions() {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare PostGIS test database.", exception);
        }
    }
}
