package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
class PlaceRecommendationTrustScoreRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    );

    @BeforeAll
    static void prepareDatabase() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void 승인_제보는_정책이_없는_제보자를_포함하고_제보자별_한_번만_집계한다() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            insertUsersAndPlace(statement);
            statement.executeUpdate("""
                    INSERT INTO reporter_moderation_policy (
                        reporter_user_id, reporter_username, submitted_count, accepted_count,
                        declined_count, false_report_count, trust_score, created_at, updated_at
                    ) VALUES (101, 'low-trust', 1, 0, 1, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            insertAcceptedReport(statement, 1001L, 101L, "PLACE_INFORMATION");
            insertAcceptedReport(statement, 1002L, 101L, "LOCATION");
            insertAcceptedReport(statement, 1003L, 102L, "OPERATING_HOURS");
            insertRejectedReport(statement);

            String query = PlaceRecommendationTrustScoreRepository.TRUST_SCORE_QUERY.replace(":placeIds", "201");
            try (ResultSet result = statement.executeQuery(query)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("placeId")).isEqualTo(201L);
                assertThat(result.getDouble("trustScore")).isEqualTo(0.54d);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void insertUsersAndPlace(Statement statement) throws Exception {
        statement.executeUpdate("""
                INSERT INTO users (
                    id, username, email, email_verified, password, birth_year,
                    language, country, created_at, updated_at, role, banned
                ) VALUES
                    (101, 'low-trust', 'low@example.com', true, 'password', 1990,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'USER', false),
                    (102, 'new-reporter', 'new@example.com', true, 'password', 1990,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'USER', false),
                    (103, 'rejected-reporter', 'rejected@example.com', true, 'password', 1990,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'USER', false),
                    (104, 'reviewer', 'reviewer@example.com', true, 'password', 1990,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ADMIN', false)
                """);
        statement.executeUpdate("""
                INSERT INTO map_place (
                    map_place_id, place_name, address, latitude, longitude, registrant, photo_count
                ) VALUES (201, 'trust-place', 'address', 35.1, 128.1, 'reviewer', 0)
                """);
    }

    private void insertAcceptedReport(Statement statement, Long id, Long reporterId, String type) throws Exception {
        statement.executeUpdate("""
                INSERT INTO visitor_verification_report (
                    id, reporter_user_id, place_id, report_type, description, status,
                    reviewer_admin_user_id, created_at, reviewed_at, updated_at, version
                ) VALUES (%d, %d, 201, '%s', 'accepted', 'ACCEPTED', 104,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """.formatted(id, reporterId, type));
    }

    private void insertRejectedReport(Statement statement) throws Exception {
        statement.executeUpdate("""
                INSERT INTO visitor_verification_report (
                    id, reporter_user_id, place_id, report_type, description, status,
                    reviewer_admin_user_id, review_note, created_at, reviewed_at, updated_at, version
                ) VALUES (1004, 103, 201, 'OTHER', 'rejected', 'REJECTED', 104, 'invalid',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
    }
}
