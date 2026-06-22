package com.typenull.pingdom.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayMigrationIntegrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    @Test
    void appliesAllMigrationsToPostgisDatabase() throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("8");
        assertThat(result.migrationsExecuted).isEqualTo(8);

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_extension
                        WHERE extname = 'postgis'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_extension
                        WHERE extname = 'pg_trgm'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'status'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'user_id'
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'withdrawn_at'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'location'
                          AND udt_name = 'geometry'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'registrant'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_image'
                          AND column_name = 'title'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place'::regclass
                          AND conname = 'uk_map_place_kakao_place_id'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'ban_type'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'ban_expires_at'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'user_sanction_history'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_name_trgm'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_address_trgm'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_category_lower'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_latitude_longitude'
                    )
                    """)).isTrue();
        }
    }

    private boolean queryBoolean(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBoolean(1);
        }
    }
}
