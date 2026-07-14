package com.typenull.pingdom.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
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
        assertThat(result.targetSchemaVersion).isEqualTo("38");
        assertThat(result.migrationsExecuted).isEqualTo(38);

        assertPostMigrationSchema();
    }

    @Test
    void baselinesExistingVersionOneSchemaAndAppliesIncrementalMigrations() throws Exception {
        executeBaselineSchemaScript();

        MigrateResult result = migrate(true);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("38");
        assertThat(result.migrationsExecuted).isEqualTo(37);

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

    @Test
    void preservesExistingPlacesWhenApplyingTouristInformationMigration() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("27")
                .load()
                .migrate();

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        place_name, address, category, latitude, longitude,
                        user_id, registrant, photo_count
                    ) VALUES (
                        '기존 장소', '기존 주소', 'legacy-free-text', 35.1801, 128.1078,
                        1, 'legacy-user', 0
                    )
                    """);
        }

        MigrateResult result = migrate(false);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("38");
        assertThat(result.migrationsExecuted).isEqualTo(11);
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'merchant_owner_profile'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'merchant_owner_place'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_owner_place'::regclass
                      AND conname IN (
                          'fk_merchant_owner_place_place',
                          'fk_merchant_owner_place_profile'
                      )
                      AND contype = 'f'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM map_place
                        WHERE place_name = '기존 장소'
                          AND category = 'legacy-free-text'
                          AND english_name IS NULL
                          AND tourist_summary IS NULL
                          AND road_address IS NULL
                          AND jibun_address IS NULL
                          AND postal_code IS NULL
                          AND geocoding_source = 'LEGACY'
                          AND operating_status = 'OPERATING'
                          AND operating_status_checked_at IS NULL
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM map_place_tourist_category
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM map_place_tourist_guard
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM map_place_regular_operating_hour
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM map_place_operating_exception
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM place_event
                    )
                    """)).isTrue();
        }
    }

    @Test
    void touristGuardPreventsLegacyDeleteForScalarOnlyInformation() throws Exception {
        migrate(false);

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        map_place_id, place_name, address, category, english_name,
                        latitude, longitude, user_id, registrant, photo_count
                    ) VALUES (
                        900001, '롤백 보호 장소', '롤백 보호 주소', 'legacy-free-text', 'Rollback Safe Place',
                        35.1801, 128.1078, 1, 'rollback-user', 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO map_place_tourist_guard (map_place_id, guard_key)
                    VALUES (900001, 'ACTIVE')
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    DELETE FROM map_place
                    WHERE map_place_id = 900001
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM map_place
                        WHERE map_place_id = 900001
                          AND english_name = 'Rollback Safe Place'
                    )
                    """)).isTrue();
        }
    }

    private MigrateResult migrate(boolean baselineOnMigrate) {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
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
                          AND column_name = 'operating_status'
                          AND character_maximum_length = 30
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'operating_status_checked_at'
                          AND data_type = 'timestamp without time zone'
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_operating_status'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_operating_status_not_null'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_regular_operating_hour'
                          AND column_name = 'day_of_week'
                          AND character_maximum_length = 9
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.columns
                    WHERE table_name = 'map_place_regular_operating_hour'
                      AND column_name IN ('opens_at', 'closes_at')
                      AND data_type = 'time without time zone'
                      AND is_nullable = 'NO'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_regular_operating_hour'::regclass
                          AND conname = 'pk_map_place_regular_operating_hour'
                          AND contype = 'p'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_operating_exception'
                          AND column_name = 'exception_date'
                          AND data_type = 'date'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_operating_exception'::regclass
                          AND conname = 'uk_map_place_operating_exception_date'
                          AND contype = 'u'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_operating_exception_hour'::regclass
                          AND conname = 'fk_map_place_operating_exception_hour_exception'
                          AND contype = 'f'
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
                        FROM pg_constraint
                        WHERE conrelid = 'map_image'::regclass
                          AND conname = 'uk_map_image_user_place'
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
                    SELECT COUNT(*) = 4
                    FROM information_schema.columns
                    WHERE table_name = 'map_place'
                      AND column_name IN ('road_address', 'jibun_address', 'postal_code', 'geocoding_source')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'geocoding_source'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_geocoding_source'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_geocoding_source_not_null'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_road_address_trgm'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place'
                          AND indexname = 'idx_map_place_jibun_address_trgm'
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
                        FROM information_schema.columns
                        WHERE table_name = 'map_image'
                          AND column_name = 'thumbnail_url'
                          AND character_maximum_length = 500
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_image'
                          AND column_name = 'thumbnail_s3_key'
                          AND character_maximum_length = 500
                          AND is_nullable = 'YES'
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
                        FROM information_schema.columns
                        WHERE table_name = 'place_recommendation_traffic_policy'
                          AND column_name = 'enabled'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'place_recommendation_traffic_policy'
                          AND column_name = 'fallback_version'
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
                        WHERE table_name = 'post_report'
                          AND column_name = 'created_at'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'post_report'
                          AND column_name = 'created_at'
                          AND column_default IS NOT NULL
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'post_report'
                          AND indexname = 'idx_post_report_reported_image_status'
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
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'users'
                          AND column_name = 'local_password_enabled'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'admin_recommendation_policy_change_history'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'admin_recommendation_policy_change_history'
                          AND column_name = 'reason'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'admin_recommendation_policy_change_history'
                          AND indexname = 'idx_admin_recommendation_policy_change_history_version'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'notification_delivery'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'notification_delivery'
                          AND column_name = 'recipient_hash'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'notification_delivery'
                          AND indexname = 'idx_notification_delivery_channel_status_created'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'privacy_processing_history'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'privacy_processing_history'
                          AND column_name = 'action'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'privacy_processing_history'
                          AND column_name = 'created_at'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'privacy_processing_history'
                          AND indexname = 'idx_privacy_processing_history_action_created'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_recommendation_exposure'
                          AND indexname = 'idx_place_recommendation_exposure_metric_aggregation'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_recommendation_click'
                          AND indexname = 'idx_place_recommendation_click_metric_aggregation'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_recommendation_conversion'
                          AND indexname = 'idx_place_recommendation_conversion_metric_aggregation'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'english_name'
                          AND character_maximum_length = 150
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place'
                          AND column_name = 'tourist_summary'
                          AND character_maximum_length = 500
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_tourist_category'
                          AND column_name = 'map_place_id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_tourist_category'
                          AND column_name = 'tourist_category'
                          AND character_maximum_length = 30
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_category'::regclass
                          AND conname = 'pk_map_place_tourist_category'
                          AND contype = 'p'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_category'::regclass
                          AND conname = 'fk_map_place_tourist_category_place'
                          AND contype = 'f'
                          AND confrelid = 'map_place'::regclass
                          AND confdeltype = 'a'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_category'::regclass
                          AND conname = 'ck_map_place_tourist_category_value'
                          AND contype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_tourist_guard'
                          AND column_name = 'map_place_id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'map_place_tourist_guard'
                          AND column_name = 'guard_key'
                          AND character_maximum_length = 16
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_guard'::regclass
                          AND conname = 'pk_map_place_tourist_guard'
                          AND contype = 'p'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_guard'::regclass
                          AND conname = 'fk_map_place_tourist_guard_place'
                          AND contype = 'f'
                          AND confrelid = 'map_place'::regclass
                          AND confdeltype = 'a'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'map_place_tourist_guard'::regclass
                          AND conname = 'ck_map_place_tourist_guard_key'
                          AND contype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_class c
                        JOIN pg_index i ON i.indexrelid = c.oid
                        WHERE c.relname = 'idx_map_place_english_name_trgm'
                          AND i.indisvalid = true
                          AND i.indisready = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'place_event'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 8
                    FROM information_schema.columns
                    WHERE table_name = 'place_event'
                      AND column_name IN (
                          'map_place_id', 'title', 'event_type', 'start_at',
                          'end_at', 'publication_status', 'created_at', 'updated_at'
                      )
                      AND is_nullable = 'NO'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_event'::regclass
                          AND conname = 'fk_place_event_place'
                          AND contype = 'f'
                          AND confrelid = 'map_place'::regclass
                          AND confdeltype = 'a'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'place_event'::regclass
                      AND conname IN (
                          'ck_place_event_type',
                          'ck_place_event_publication_status',
                          'ck_place_event_period'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_event'
                          AND indexname = 'idx_place_event_place_id'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_event'
                          AND indexname = 'idx_place_event_public_discovery'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'user_travel_purpose'
                          AND column_name = 'user_id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'user_travel_purpose'
                          AND column_name = 'travel_purpose'
                          AND character_maximum_length = 30
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_travel_purpose'::regclass
                          AND conname = 'pk_user_travel_purpose'
                          AND contype = 'p'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_travel_purpose'::regclass
                          AND conname = 'fk_user_travel_purpose_user'
                          AND contype = 'f'
                          AND confrelid = 'users'::regclass
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_travel_purpose'::regclass
                          AND conname = 'ck_user_travel_purpose_value'
                          AND contype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'user_travel_schedule'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 7
                    FROM information_schema.columns
                    WHERE table_name = 'user_travel_schedule'
                      AND column_name IN (
                          'user_id', 'start_date', 'end_date', 'state', 'created_at', 'updated_at', 'version'
                      )
                      AND is_nullable = 'NO'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_travel_schedule'::regclass
                          AND conname = 'fk_user_travel_schedule_user'
                          AND contype = 'f'
                          AND confrelid = 'users'::regclass
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'user_travel_schedule'::regclass
                      AND conname IN ('ck_user_travel_schedule_period', 'ck_user_travel_schedule_state')
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'user_travel_schedule'
                          AND indexname = 'idx_user_travel_schedule_user_period'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'user_current_activity_intent'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM information_schema.columns
                    WHERE table_name = 'user_current_activity_intent'
                      AND column_name IN (
                          'user_id', 'activity_intent', 'expires_at', 'created_at', 'updated_at'
                      )
                      AND is_nullable = 'NO'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_current_activity_intent'::regclass
                          AND conname = 'uk_user_current_activity_intent_user'
                          AND contype = 'u'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_current_activity_intent'::regclass
                          AND conname = 'fk_user_current_activity_intent_user'
                          AND contype = 'f'
                          AND confrelid = 'users'::regclass
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'user_current_activity_intent'::regclass
                          AND conname = 'ck_user_current_activity_intent_value'
                          AND contype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'user_current_activity_intent'
                          AND indexname = 'idx_user_current_activity_intent_expires_at'
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
