package com.typenull.pingdom.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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

    @BeforeAll
    static void ensureRequiredExtensions() throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        }
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ DECLARE
                        r RECORD;
                    BEGIN
                        FOR r IN (
                            SELECT tablename
                            FROM pg_tables
                            WHERE schemaname = 'public'
                              AND tablename != 'spatial_ref_sys'
                        ) LOOP
                            EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
                        END LOOP;
                    END $$;
                    """);
        }
    }

    @Test
    void appliesAllMigrationsToPostgisDatabase() throws Exception {
        MigrateResult result = migrate(false);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("15");
        assertThat(result.migrationsExecuted).isEqualTo(15);

        assertPostMigrationSchema();
    }

    @Test
    void baselinesExistingVersionOneSchemaAndAppliesIncrementalMigrations() throws Exception {
        executeBaselineSchemaScript();

        MigrateResult result = migrate(true);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("15");
        assertThat(result.migrationsExecuted).isEqualTo(14);

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM flyway_schema_history
                        WHERE version = '1'
                          AND type = 'BASELINE'
                          AND success = true
                    )
                    """)).isTrue();
        }
        assertPostMigrationSchema();
    }

    private MigrateResult migrate(boolean baselineOnMigrate) {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .load();

        return flyway.migrate();
    }

    private void executeBaselineSchemaScript() throws Exception {
        try (Connection connection = postgres.createConnection("")) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V1__baseline_schema.sql")
            );
        }
    }

    private void assertPostMigrationSchema() throws Exception {
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
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'admin_audit_log'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'admin_audit_log'
                          AND column_name = 'request_id'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'admin_audit_log'
                          AND indexname = 'idx_admin_audit_log_target_created'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_image'
                          AND column_name = 'visibility_status'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'reporter_moderation_policy'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'report_appeal'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'place_recommendation_traffic_policy'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'admin_place_merge_history'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'admin_place_merge_history'
                          AND column_name = 'restored'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'admin_place_merge_history'
                          AND indexname = 'idx_admin_place_merge_history_created'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'post_report'
                          AND column_name = 'report_score'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'report_count'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'unaccepted_report_count'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'password_reset_token'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'password_reset_token'
                          AND column_name = 'token_hash'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'password_reset_token'::regclass
                          AND conname = 'fk_password_reset_token_user'
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
