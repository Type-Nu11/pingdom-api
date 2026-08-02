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

    private static final String LATEST_MIGRATION_VERSION = "78";

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
        assertThat(result.targetSchemaVersion).isEqualTo(LATEST_MIGRATION_VERSION);
        assertThat(result.migrationsExecuted).isEqualTo(78);

        assertPostMigrationSchema();
    }

    @Test
    void baselinesExistingVersionOneSchemaAndAppliesIncrementalMigrations() throws Exception {
        executeBaselineSchemaScript();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        place_name, address, category, latitude, longitude,
                        user_id, registrant, photo_count, location
                    ) VALUES (
                        '기존 좌표 장소', '기존 주소', '카페', 35.1801, 128.1078,
                        1, 'migration-test', 0, NULL
                    )
                    """);
        }

        MigrateResult result = migrate(true);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo(LATEST_MIGRATION_VERSION);
        assertThat(result.migrationsExecuted).isEqualTo(76);

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.columns
                    WHERE table_name = 'place_recommendation_feature_log'
                      AND column_name IN ('benefit_score', 'availability_score')
                      AND is_nullable = 'NO'
                      AND column_default IS NOT NULL
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'place_recommendation_feature_log'::regclass
                      AND conname IN (
                          'ck_recommendation_feature_log_benefit_score',
                          'ck_recommendation_feature_log_availability_score'
                      )
                      AND contype = 'c'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM flyway_schema_history
                        WHERE version = '1'
                          AND type = 'BASELINE'
                          AND success = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT ST_Equals(
                        location,
                        ST_SetSRID(ST_MakePoint(128.1078, 35.1801), 4326)
                    )
                    FROM map_place
                    WHERE registrant = 'migration-test'
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
        assertThat(result.targetSchemaVersion).isEqualTo(LATEST_MIGRATION_VERSION);
        assertThat(result.migrationsExecuted).isEqualTo(51);
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
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'visitor_verification_report'
                          AND index_class.relname = 'idx_visitor_verification_report_accepted_place_reporter'
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%ACCEPTED%'
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
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'merchant_place_claim'::regclass
                          AND conname = 'fk_merchant_place_claim_previous_owner'
                          AND contype = 'f'
                          AND confrelid = 'merchant_owner_profile'::regclass
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'merchant_place_claim'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'merchant_place_claim'
                          AND indexname = 'uq_merchant_place_claim_pending_place'
                    )
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
                          AND discovery_status = 'VISIBLE'
                          AND primary_information_source = 'LEGACY'
                          AND information_verification_status = 'UNVERIFIED'
                          AND information_verified_at IS NULL
                          AND information_verified_by_admin_user_id IS NULL
                          AND information_evidence_updated_at IS NULL
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
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 8
                    FROM information_schema.columns
                    WHERE table_name = 'location_check_in'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'location_check_in'::regclass
                      AND conname IN (
                          'fk_location_check_in_tourist',
                          'fk_location_check_in_place'
                      )
                      AND contype = 'f'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'location_check_in'::regclass
                          AND conname = 'uq_location_check_in_daily'
                          AND contype = 'u'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'location_check_in'::regclass
                      AND conname IN (
                          'ck_location_check_in_status',
                          'ck_location_check_in_distance'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'location_check_in'
                      AND indexname IN (
                          'idx_location_check_in_tourist_recorded',
                          'idx_location_check_in_place_recorded'
                      )
                    """)).isTrue();
        }
    }

    @Test
    void backfillsExistingPlaceImagesIntoSeparatedPlaceMedia() throws Exception {
        migrateTo("55");

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        map_place_id, place_name, address, image_url,
                        latitude, longitude, registrant, photo_count
                    ) VALUES (
                        930001, '미디어 장소', '경상남도 진주시 미디어로 1',
                        'https://example.com/place-explore.jpg',
                        35.1801, 128.1078, 'media-user', 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO map_image (
                        map_image_id, image_url, s3_key, thumbnail_url, thumbnail_s3_key,
                        title, description, user_id, username, created_time, like_count,
                        visibility_status, map_place_id
                    ) VALUES (
                        930001, 'https://example.com/verify.jpg', 'map/verify.jpg',
                        'https://example.com/verify-thumb.jpg', 'map/thumb/verify.jpg',
                        '검증 사진', '장소 검증용 사진', 930001, 'media-user',
                        TIMESTAMP '2026-07-21 10:00:00', 0, 'ACTIVE', 930001
                    )
                    """);
        }

        MigrateResult result = migrate(false);

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo(LATEST_MIGRATION_VERSION);
        assertThat(result.migrationsExecuted).isEqualTo(23);
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.tables
                    WHERE table_name IN ('merchant_brand', 'popup_campaign')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'popup_campaign'::regclass
                      AND conname IN (
                          'fk_popup_campaign_brand_owner',
                          'ck_popup_campaign_period',
                          'ck_popup_campaign_status'
                      )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'popup_campaign'
                      AND indexname IN (
                          'idx_popup_campaign_public_period',
                          'idx_popup_campaign_place_public_period'
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM place_media
                        WHERE map_place_id = 930001
                          AND purpose = 'EXPLORATION'
                          AND image_url = 'https://example.com/place-explore.jpg'
                          AND source_map_image_id IS NULL
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM place_media
                        WHERE map_place_id = 930001
                          AND purpose = 'VERIFICATION'
                          AND image_url = 'https://example.com/verify.jpg'
                          AND s3_key = 'map/verify.jpg'
                          AND thumbnail_url = 'https://example.com/verify-thumb.jpg'
                          AND thumbnail_s3_key = 'map/thumb/verify.jpg'
                          AND source_map_image_id = 930001
                          AND created_at = TIMESTAMP '2026-07-21 10:00:00'
                    )
                    """)).isTrue();
        }
    }

    @Test
    void backfillsExistingMerchantPlaceClaimBeforeValidatingOwnershipConstraints() throws Exception {
        migrateTo("41");

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            insertMerchantPlaceClaimFixture(statement, false);
        }

        MigrateResult result = migrate(false);

        assertThat(result.success).isTrue();
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM merchant_place_claim
                        WHERE id = 920001
                          AND claim_type = 'INITIAL'
                          AND previous_owner_user_id IS NULL
                    )
                    """)).isTrue();
        }
    }

    @Test
    void validationMigrationRejectsExistingOwnershipConstraintViolation() throws Exception {
        migrateTo("42");

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            insertMerchantPlaceClaimFixture(statement, true);
            statement.execute("ALTER TABLE merchant_place_claim DROP CONSTRAINT ck_merchant_place_claim_transfer_owner");
            statement.executeUpdate("""
                    UPDATE merchant_place_claim
                    SET previous_owner_user_id = 920002
                    WHERE id = 920001
                    """);
            statement.execute("""
                    ALTER TABLE merchant_place_claim
                    ADD CONSTRAINT ck_merchant_place_claim_transfer_owner
                    CHECK (
                        claim_type = 'OWNERSHIP_TRANSFER'
                        OR previous_owner_user_id IS NULL
                    ) NOT VALID
                    """);
        }

        assertThatThrownBy(() -> migrate(false))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .hasStackTraceContaining("ck_merchant_place_claim_transfer_owner");
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

    @Test
    void deletingCouponRedeemerPreservesRedemptionHistory() throws Exception {
        migrate(false);

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (
                        id, username, email, email_verified, password, birth_year,
                        language, country, created_at, updated_at, role, banned
                    ) VALUES
                        (910001, 'offer-owner', 'offer-owner@example.com', true, 'password', 1990,
                         'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MERCHANT_OWNER', false),
                        (910002, 'coupon-tourist', 'coupon-tourist@example.com', true, 'password', 1995,
                         'en', 'US', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'USER', false),
                        (910003, 'coupon-redeemer', 'coupon-redeemer@example.com', true, 'password', 1991,
                         'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MERCHANT_OWNER', false)
                    """);
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        map_place_id, place_name, address, latitude, longitude, registrant, photo_count
                    ) VALUES (910001, 'Offer 장소', '서울시 중구', 37.5, 127.0, 'offer-owner', 0)
                    """);
            statement.executeUpdate("""
                    INSERT INTO merchant_owner_profile (
                        user_id, business_name, display_name, contact_email, contact_phone,
                        status, created_at, updated_at
                    ) VALUES (
                        910001, 'Offer 상점', 'Offer 사장님', 'offer-owner@example.com', '010-0000-0000',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO tourist_offer (
                        id, merchant_owner_user_id, place_id, title, description, benefit_description,
                        status, starts_at, ends_at, total_quantity, issued_quantity,
                        coupon_validity_days, created_at, updated_at
                    ) VALUES (
                        910001, 910001, 910001, 'Offer', '설명', '혜택',
                        'PUBLISHED', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '7 days',
                        10, 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO tourist_coupon (
                        id, offer_id, user_id, code, status, issued_at, expires_at, redeemed_at, redeemed_by
                    ) VALUES (
                        910001, 910001, 910002, '3fa85f64-5717-4562-b3fc-2c963f66afa6', 'REDEEMED',
                        CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP, 910003
                    )
                    """);

            statement.executeUpdate("DELETE FROM users WHERE id = 910003");

            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM tourist_coupon
                        WHERE id = 910001
                          AND status = 'REDEEMED'
                          AND redeemed_at IS NOT NULL
                          AND redeemed_by IS NULL
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

    private MigrateResult migrateTo(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .target(target)
                .load()
                .migrate();
    }

    private void insertMerchantPlaceClaimFixture(Statement statement, boolean includePreviousOwner) throws Exception {
        statement.executeUpdate("""
                INSERT INTO users (
                    id, username, email, email_verified, password, birth_year,
                    language, country, created_at, updated_at, role, banned
                ) VALUES
                    (920001, 'claim-owner', 'claim-owner@example.com', true, 'password', 1990,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MERCHANT_OWNER', false),
                    (920002, 'previous-owner', 'previous-owner@example.com', true, 'password', 1991,
                     'ko', 'KR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MERCHANT_OWNER', false)
                """);
        statement.executeUpdate("""
                INSERT INTO map_place (
                    map_place_id, place_name, address, latitude, longitude, registrant, photo_count
                ) VALUES (920001, 'Claim 장소', '서울시 중구', 37.5, 127.0, 'claim-owner', 0)
                """);
        statement.executeUpdate("""
                INSERT INTO merchant_owner_profile (
                    user_id, business_name, display_name, contact_email, contact_phone,
                    status, created_at, updated_at
                ) VALUES
                    (920001, 'Claim 상점', 'Claim 사장님', 'claim-owner@example.com', '010-0000-0001',
                     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (920002, '이전 상점', '이전 사장님', 'previous-owner@example.com', '010-0000-0002',
                     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        if (includePreviousOwner) {
            statement.executeUpdate("""
                    INSERT INTO merchant_place_claim (
                        id, merchant_owner_user_id, place_id, claim_type, previous_owner_user_id,
                        claim_reason, status, created_at, updated_at
                    ) VALUES (
                        920001, 920001, 920001, 'INITIAL', NULL,
                        '소유권 확인', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            return;
        }
        statement.executeUpdate("""
                INSERT INTO merchant_place_claim (
                    id, merchant_owner_user_id, place_id, claim_reason, status, created_at, updated_at
                ) VALUES (
                    920001, 920001, 920001, '소유권 확인', 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
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
                    SELECT COUNT(*) = 2
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('payment_transaction', 'settlement_ledger_entry')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conname IN (
                        'uq_payment_tourist_idempotency',
                        'uq_payment_provider_reference',
                        'uq_settlement_payment_type',
                        'ck_settlement_amounts'
                    )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'payment_transaction'
                          AND indexname = 'uq_payment_reservation_active'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'place_recommendation_feature_log'
                          AND column_name = 'boost_score'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint constraint_metadata
                    WHERE constraint_metadata.conname IN (
                        'fk_boost_selection_place',
                        'fk_boost_execution_place'
                    )
                      AND constraint_metadata.confrelid = 'map_place'::regclass
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'place_recommendation_feature_log'
                          AND column_name = 'trust_score'
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_recommendation_feature_log'::regclass
                          AND conname = 'ck_recommendation_feature_log_trust_score'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'visit_evidence'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'visit_evidence'::regclass
                      AND conname IN (
                          'fk_visit_evidence_check_in',
                          'fk_visit_evidence_tourist',
                          'uq_visit_evidence_check_in',
                          'ck_visit_evidence_expiration'
                      )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'visit_evidence'::regclass
                          AND conname = 'fk_visit_evidence_check_in'
                          AND confdeltype = 'r'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'visitor_verification_report'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 8
                    FROM pg_constraint
                    WHERE conrelid = 'visitor_verification_report'::regclass
                      AND conname IN (
                          'ck_visitor_verification_report_type',
                          'ck_visitor_verification_report_status',
                          'ck_visitor_verification_report_review',
                          'ck_visitor_verification_report_wait_time',
                          'ck_visitor_verification_report_language_code',
                          'ck_visitor_verification_report_coupon_usage',
                          'ck_visitor_verification_report_crowd_level',
                          'ck_visitor_verification_report_structured_value'
                      )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'visitor_verification_report'
                          AND index_class.relname = 'uq_visitor_verification_report_active'
                          AND index_metadata.indisunique = true
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%SUBMITTED%'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'visitor_verification_report_correction'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 9
                    FROM pg_constraint
                    WHERE conrelid = 'visitor_verification_report_correction'::regclass
                      AND conname IN (
                          'ck_visitor_verification_report_correction_type',
                          'ck_visitor_verification_report_correction_status',
                          'ck_visitor_verification_report_correction_description',
                          'ck_visitor_verification_report_correction_wait_time',
                          'ck_visitor_verification_report_correction_language',
                          'ck_visitor_verification_report_correction_coupon',
                          'ck_visitor_verification_report_correction_crowd',
                          'ck_visitor_verification_report_correction_structured_value',
                          'ck_visitor_verification_report_correction_review'
                      )
                      AND contype = 'c'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'visitor_verification_report_correction'
                          AND index_class.relname = 'uq_visitor_verification_report_correction_active'
                          AND index_metadata.indisunique = true
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%SUBMITTED%'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 16
                    FROM information_schema.columns
                    WHERE table_name = 'place_operating_notice'
                      AND (
                          (column_name = 'place_operating_notice_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('map_place_id', 'created_by_user_id')
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'updated_by_user_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name = 'notice_type'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name IN ('severity', 'status')
                              AND data_type = 'character varying'
                              AND character_maximum_length = 20
                              AND is_nullable = 'NO')
                          OR (column_name = 'message'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'NO')
                          OR (column_name = 'cancel_reason'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name IN ('starts_at', 'expires_at', 'created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('expired_at', 'canceled_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'YES')
                          OR (column_name = 'version'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 6
                    FROM pg_constraint
                    WHERE conrelid = 'place_operating_notice'::regclass
                      AND conname IN (
                          'fk_place_operating_notice_place',
                          'ck_place_operating_notice_type',
                          'ck_place_operating_notice_severity',
                          'ck_place_operating_notice_status',
                          'ck_place_operating_notice_message',
                          'ck_place_operating_notice_period'
                      )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_operating_notice'::regclass
                          AND conname = 'ck_place_operating_notice_lifecycle'
                          AND contype = 'c'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'place_operating_notice'
                          AND index_class.relname = 'uq_place_operating_notice_active_type'
                          AND index_metadata.indisunique = true
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%SCHEDULED%'
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%ACTIVE%'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_indexes
                    WHERE indexname IN (
                        'idx_place_operating_notice_place_status_period',
                        'idx_place_operating_notice_expiration',
                        'idx_place_operating_notice_creator_created'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'scout_field_report'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'place_information_reverification_request'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 8
                    FROM pg_constraint
                    WHERE conrelid = 'place_information_reverification_request'::regclass
                      AND conname IN (
                          'fk_place_information_reverification_place',
                          'fk_place_information_reverification_evidence',
                          'ck_place_information_reverification_status',
                          'ck_place_information_reverification_reason',
                          'ck_place_information_reverification_due',
                          'ck_place_information_reverification_reminder',
                          'ck_place_information_reverification_response',
                          'ck_place_information_reverification_terminal'
                      )
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'place_information_reverification_request'
                          AND index_class.relname = 'uq_place_information_reverification_active'
                          AND index_metadata.indisunique = true
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%REQUESTED%'
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%RESPONDED%'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'scout_field_report'::regclass
                      AND conname IN (
                          'ck_scout_field_report_type',
                          'ck_scout_field_report_status',
                          'ck_scout_field_report_description',
                          'ck_scout_field_report_review'
                      )
                      AND contype = 'c'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'scout_field_report'::regclass
                      AND conname IN (
                          'fk_scout_field_report_scout',
                          'fk_scout_field_report_place',
                          'fk_scout_field_report_reviewer'
                      )
                      AND contype = 'f'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'scout_field_report'::regclass
                      AND conname IN (
                          'fk_scout_field_report_scout',
                          'fk_scout_field_report_reviewer'
                      )
                      AND contype = 'f'
                      AND confdeltype = 'n'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index index_metadata
                        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
                        JOIN pg_class table_class ON table_class.oid = index_metadata.indrelid
                        WHERE table_class.relname = 'scout_field_report'
                          AND index_class.relname = 'uq_scout_field_report_active'
                          AND index_metadata.indisunique = true
                          AND pg_get_expr(index_metadata.indpred, index_metadata.indrelid) LIKE '%SUBMITTED%'
                    )
                    """)).isTrue();
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
                        WHERE table_name = 'map_place'
                          AND column_name = 'discovery_status'
                          AND character_maximum_length = 20
                          AND is_nullable = 'NO'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_discovery_status'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'ck_map_place_discovery_status_not_null'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'map_place'
                      AND indexname IN ('idx_map_place_public_latest', 'idx_map_place_public_popular')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'map_place_tourist_category'
                          AND indexname = 'idx_map_place_tourist_category_filter'
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
                        FROM pg_constraint
                        WHERE conrelid = 'tourist_coupon'::regclass
                          AND conname = 'fk_tourist_coupon_redeemer'
                          AND contype = 'f'
                          AND confrelid = 'users'::regclass
                          AND confdeltype = 'n'
                    )
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
                        FROM information_schema.tables
                        WHERE table_name = 'place_duplicate_candidate'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 7
                    FROM pg_constraint
                    WHERE conrelid = 'place_duplicate_candidate'::regclass
                      AND conname IN (
                          'ck_place_duplicate_candidate_pair',
                          'ck_place_duplicate_candidate_match_reason',
                          'ck_place_duplicate_candidate_confidence',
                          'ck_place_duplicate_candidate_distance',
                          'ck_place_duplicate_candidate_status',
                          'ck_place_duplicate_candidate_review',
                          'ck_place_duplicate_candidate_merge'
                      )
                      AND contype = 'c'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'place_duplicate_candidate'
                          AND indexname = 'uq_place_duplicate_candidate_pair'
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
                        WHERE table_name = 'place_recommendation_conversion'
                          AND column_name = 'place_recommendation_feature_log_id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'YES'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_recommendation_conversion'::regclass
                          AND conname = 'fk_recommendation_conversion_feature_log'
                          AND contype = 'f'
                          AND confdeltype = 'n'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE indexname IN (
                        'idx_recommendation_feature_log_attribution',
                        'idx_recommendation_conversion_feature_log'
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
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM information_schema.columns
                    WHERE table_name = 'merchant_owner_profile'
                      AND (
                          (column_name = 'onboarding_status'
                              AND is_nullable = 'NO'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 20
                              AND column_default = '''NOT_STARTED''::character varying')
                          OR (column_name = 'onboarding_completion_rate'
                              AND is_nullable = 'NO'
                              AND data_type = 'integer'
                              AND column_default = '0')
                          OR (column_name = 'onboarding_completed_at'
                              AND is_nullable = 'YES'
                              AND data_type = 'timestamp without time zone')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_owner_profile'::regclass
                      AND conname IN (
                          'ck_merchant_owner_profile_onboarding_status',
                          'ck_merchant_owner_profile_onboarding_completion_rate',
                          'ck_merchant_owner_profile_onboarding_completed_at'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'merchant_owner_profile'
                          AND indexname = 'idx_merchant_owner_profile_onboarding'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM information_schema.columns
                    WHERE table_name = 'merchant_owner_place'
                      AND (
                          (column_name = 'operational_quality_status'
                              AND is_nullable = 'NO'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 20
                              AND column_default = '''UNMEASURED''::character varying')
                          OR (column_name IN (
                                  'reservation_response_rate',
                                  'reservation_cancellation_rate',
                                  'no_show_rate'
                              )
                              AND is_nullable = 'NO'
                              AND data_type = 'integer'
                              AND column_default = '0')
                          OR (column_name = 'quality_evaluated_at'
                              AND is_nullable = 'YES'
                              AND data_type = 'timestamp without time zone')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_owner_place'::regclass
                      AND conname IN (
                          'ck_merchant_owner_place_operational_quality_status',
                          'ck_merchant_owner_place_reservation_response_rate',
                          'ck_merchant_owner_place_reservation_cancellation_rate',
                          'ck_merchant_owner_place_no_show_rate'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'merchant_owner_place'
                          AND indexname = 'idx_merchant_owner_place_quality'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'merchant_verification'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'merchant_verification'
                          AND column_name = 'business_name'
                          AND is_nullable = 'NO'
                          AND character_maximum_length = 100
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'merchant_verification'
                          AND column_name = 'business_registration_number'
                          AND is_nullable = 'NO'
                          AND character_maximum_length = 255
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_verification'::regclass
                      AND conname IN (
                          'ck_merchant_verification_identity_status',
                          'ck_merchant_verification_business_status'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'merchant_verification'::regclass
                          AND conname = 'fk_merchant_verification_profile'
                          AND contype = 'f'
                          AND confrelid = 'merchant_owner_profile'::regclass
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'merchant_place_claim'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_place_claim'::regclass
                      AND conname IN (
                          'fk_merchant_place_claim_profile',
                          'fk_merchant_place_claim_place',
                          'fk_merchant_place_claim_reviewer',
                          'fk_merchant_place_claim_previous_owner'
                      )
                      AND contype = 'f'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'merchant_place_claim'::regclass
                          AND conname = 'ck_merchant_place_claim_status'
                          AND contype = 'c'
                          AND convalidated = true
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'merchant_place_claim'
                          AND column_name = 'claim_type'
                          AND is_nullable = 'NO'
                          AND character_maximum_length = 30
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'merchant_place_claim'::regclass
                      AND conname IN (
                          'ck_merchant_place_claim_type',
                          'ck_merchant_place_claim_transfer_owner'
                      )
                      AND contype = 'c'
                      AND convalidated = true
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'merchant_place_claim'
                          AND indexname = 'uq_merchant_place_claim_pending_place'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.tables
                    WHERE table_name IN ('tourist_offer', 'tourist_coupon')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'tourist_coupon'::regclass
                      AND conname IN (
                          'uq_tourist_coupon_offer_user',
                          'uq_tourist_coupon_code'
                      )
                      AND contype = 'u'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'tourist_offer'::regclass
                      AND conname IN (
                          'ck_tourist_offer_status',
                          'ck_tourist_offer_period',
                          'ck_tourist_offer_quantity',
                          'ck_tourist_offer_coupon_validity_days'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_indexes
                        WHERE tablename = 'tourist_offer'
                          AND indexname = 'idx_tourist_offer_place_public_period'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 12
                    FROM information_schema.columns
                    WHERE table_name = 'place_availability'
                      AND is_nullable = 'NO'
                      AND (
                          (column_name IN ('id', 'merchant_owner_user_id', 'place_id', 'version')
                              AND data_type = 'bigint')
                          OR (column_name IN ('starts_at', 'ends_at', 'created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone')
                          OR (column_name IN ('total_capacity', 'remaining_capacity')
                              AND data_type = 'integer')
                          OR (column_name IN ('status', 'product_type') AND data_type = 'character varying'
                              AND character_maximum_length = 20)
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'place_availability'::regclass
                      AND conname IN (
                          'ck_place_availability_period',
                          'ck_place_availability_capacity',
                          'ck_place_availability_status',
                          'ck_place_availability_product_type'
                      )
                      AND contype = 'c'
                      AND CASE conname
                          WHEN 'ck_place_availability_period' THEN
                              pg_get_constraintdef(oid) LIKE '%ends_at > starts_at%'
                          WHEN 'ck_place_availability_capacity' THEN
                              pg_get_constraintdef(oid) LIKE '%total_capacity > 0%'
                              AND pg_get_constraintdef(oid) LIKE '%remaining_capacity >= 0%'
                              AND pg_get_constraintdef(oid) LIKE '%remaining_capacity <= total_capacity%'
                          WHEN 'ck_place_availability_status' THEN
                              pg_get_constraintdef(oid) LIKE '%status%'
                              AND pg_get_constraintdef(oid) LIKE '%ACTIVE%'
                              AND pg_get_constraintdef(oid) LIKE '%INACTIVE%'
                          WHEN 'ck_place_availability_product_type' THEN
                              pg_get_constraintdef(oid) LIKE '%product_id IS NULL%'
                              AND pg_get_constraintdef(oid) LIKE '%product_type%GENERAL%'
                              AND pg_get_constraintdef(oid) LIKE '%product_id IS NOT NULL%'
                              AND pg_get_constraintdef(oid) LIKE '%TICKET%'
                              AND pg_get_constraintdef(oid) LIKE '%CLASS%'
                      END
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'place_availability'::regclass
                      AND contype = 'f'
                      AND (
                          (conname = 'fk_place_availability_merchant_owner'
                              AND confrelid = 'merchant_owner_profile'::regclass
                              AND pg_get_constraintdef(oid) =
                                  'FOREIGN KEY (merchant_owner_user_id) REFERENCES merchant_owner_profile(user_id) ON DELETE CASCADE')
                          OR (conname = 'fk_place_availability_place'
                              AND confrelid = 'map_place'::regclass
                              AND pg_get_constraintdef(oid) =
                                  'FOREIGN KEY (place_id) REFERENCES map_place(map_place_id) ON DELETE CASCADE')
                          OR (conname = 'fk_place_availability_product'
                              AND confrelid = 'reservable_product'::regclass
                              AND pg_get_constraintdef(oid) =
                                  'FOREIGN KEY (product_id, product_type) REFERENCES reservable_product(id, product_type) ON DELETE RESTRICT')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_index index_catalog
                    JOIN pg_class index_relation ON index_relation.oid = index_catalog.indexrelid
                    WHERE index_catalog.indrelid = 'place_availability'::regclass
                      AND index_catalog.indisunique = true
                      AND index_catalog.indpred IS NOT NULL
                      AND (
                          (index_relation.relname = 'uq_place_availability_legacy_slot'
                              AND pg_get_indexdef(index_catalog.indexrelid) LIKE '%product_id IS NULL%')
                          OR (index_relation.relname = 'uq_place_availability_product_slot'
                              AND pg_get_indexdef(index_catalog.indexrelid) LIKE '%product_id IS NOT NULL%')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_index index_catalog
                    JOIN pg_class index_relation ON index_relation.oid = index_catalog.indexrelid
                    WHERE index_catalog.indrelid = 'place_availability'::regclass
                      AND index_catalog.indpred IS NULL
                      AND (
                          (index_relation.relname = 'idx_place_availability_public'
                              AND pg_get_indexdef(index_catalog.indexrelid) LIKE
                                  '%USING btree (place_id, status, starts_at, ends_at, id)%')
                          OR (index_relation.relname = 'idx_place_availability_owner'
                              AND pg_get_indexdef(index_catalog.indexrelid) LIKE
                                  '%USING btree (merchant_owner_user_id, starts_at, id)%')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'place_availability'
                          AND column_name = 'product_id'
                          AND data_type = 'bigint'
                          AND is_nullable = 'YES'
                    ) AND EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_name = 'place_availability'
                          AND column_name = 'product_type'
                          AND column_default = '''GENERAL''::character varying'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 9
                    FROM information_schema.columns
                    WHERE table_name = 'reservable_product'
                      AND (
                          (column_name IN ('id', 'merchant_owner_user_id', 'place_id', 'version')
                              AND data_type = 'bigint' AND is_nullable = 'NO')
                          OR (column_name IN ('product_type', 'status')
                              AND data_type = 'character varying' AND character_maximum_length = 20
                              AND is_nullable = 'NO')
                          OR (column_name = 'name' AND data_type = 'character varying'
                              AND character_maximum_length = 100 AND is_nullable = 'NO')
                          OR (column_name IN ('created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone' AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM pg_constraint
                    WHERE conrelid = 'reservable_product'::regclass
                      AND (
                          conname IN (
                              'fk_reservable_product_merchant_owner',
                              'uq_reservable_product_id_type',
                              'ck_reservable_product_type',
                              'ck_reservable_product_status'
                          )
                          OR (conname = 'fk_reservable_product_place'
                              AND pg_get_constraintdef(oid) =
                                  'FOREIGN KEY (place_id) REFERENCES map_place(map_place_id) ON DELETE RESTRICT')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'reservable_product'
                      AND indexname IN (
                          'idx_reservable_product_owner',
                          'idx_reservable_product_place_status'
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.columns
                    WHERE table_name = 'reservation'
                      AND (
                          (column_name = 'product_id' AND data_type = 'bigint' AND is_nullable = 'YES')
                          OR (column_name = 'product_type' AND data_type = 'character varying'
                              AND character_maximum_length = 20 AND is_nullable = 'NO'
                              AND column_default = '''GENERAL''::character varying')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'reservation'::regclass
                      AND (
                          (conname = 'fk_reservation_product'
                              AND pg_get_constraintdef(oid) =
                                  'FOREIGN KEY (product_id, product_type) REFERENCES reservable_product(id, product_type) ON DELETE RESTRICT')
                          OR (conname = 'ck_reservation_product_type'
                              AND pg_get_constraintdef(oid) LIKE '%product_id IS NULL%'
                              AND pg_get_constraintdef(oid) LIKE '%product_type%GENERAL%'
                              AND pg_get_constraintdef(oid) LIKE '%product_id IS NOT NULL%'
                              AND pg_get_constraintdef(oid) LIKE '%TICKET%'
                              AND pg_get_constraintdef(oid) LIKE '%CLASS%')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM information_schema.tables
                    WHERE table_name IN ('trust_score_anomaly', 'trust_score_intervention_rule')
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 15
                    FROM information_schema.columns
                    WHERE table_name = 'trust_score_anomaly'
                      AND (
                          (column_name IN (
                              'id', 'reporter_user_id', 'submitted_count', 'accepted_count',
                              'declined_count', 'false_report_count'
                          ) AND data_type = 'bigint' AND is_nullable = 'NO')
                          OR (column_name IN ('baseline_score', 'observed_score')
                              AND data_type = 'integer' AND is_nullable = 'NO')
                          OR (column_name IN ('detected_at', 'created_at')
                              AND data_type = 'timestamp without time zone' AND is_nullable = 'NO')
                          OR (column_name = 'resolved_at'
                              AND data_type = 'timestamp without time zone' AND is_nullable = 'YES')
                          OR (column_name = 'reporter_username'
                              AND data_type = 'character varying' AND character_maximum_length = 50
                              AND is_nullable = 'NO')
                          OR (column_name = 'anomaly_type'
                              AND data_type = 'character varying' AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name = 'severity'
                              AND data_type = 'character varying' AND character_maximum_length = 20
                              AND is_nullable = 'NO')
                          OR (column_name = 'resolution_reason'
                              AND data_type = 'character varying' AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM pg_constraint
                    WHERE conrelid = 'trust_score_anomaly'::regclass
                      AND conname IN (
                          'ck_trust_score_anomaly_type',
                          'ck_trust_score_anomaly_severity',
                          'ck_trust_score_anomaly_score_range',
                          'ck_trust_score_anomaly_counts',
                          'ck_trust_score_anomaly_resolution'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'trust_score_anomaly'::regclass
                          AND conname = 'fk_trust_score_anomaly_reporter_policy'
                          AND contype = 'f'
                          AND confrelid = 'reporter_moderation_policy'::regclass
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_indexes
                    WHERE tablename = 'trust_score_anomaly'
                      AND indexname IN (
                          'idx_trust_score_anomaly_reporter_detected',
                          'idx_trust_score_anomaly_type_severity',
                          'idx_trust_score_anomaly_unresolved'
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 15
                    FROM information_schema.columns
                    WHERE table_name = 'trust_score_intervention_rule'
                      AND (
                          (column_name IN ('id', 'min_submitted_count', 'min_false_report_count', 'version')
                              AND data_type = 'bigint' AND is_nullable = 'NO')
                          OR (column_name IN ('min_trust_score', 'max_trust_score', 'priority')
                              AND data_type = 'integer' AND is_nullable = 'NO')
                          OR (column_name = 'duration_days'
                              AND data_type = 'integer' AND is_nullable = 'YES')
                          OR (column_name = 'enabled'
                              AND data_type = 'boolean' AND is_nullable = 'NO')
                          OR (column_name IN ('created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone' AND is_nullable = 'NO')
                          OR (column_name = 'rule_name'
                              AND data_type = 'character varying' AND character_maximum_length = 100
                              AND is_nullable = 'NO')
                          OR (column_name IN ('trigger_type', 'action_type')
                              AND data_type = 'character varying' AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name = 'reason'
                              AND data_type = 'character varying' AND character_maximum_length = 500
                              AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 6
                    FROM pg_constraint
                    WHERE conrelid = 'trust_score_intervention_rule'::regclass
                      AND conname IN (
                          'ck_trust_score_intervention_rule_trigger',
                          'ck_trust_score_intervention_rule_action',
                          'ck_trust_score_intervention_rule_score_range',
                          'ck_trust_score_intervention_rule_counts',
                          'ck_trust_score_intervention_rule_duration',
                          'ck_trust_score_intervention_rule_priority'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'trust_score_intervention_rule'::regclass
                          AND conname = 'uq_trust_score_intervention_rule_name'
                          AND contype = 'u'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'trust_score_intervention_rule'
                      AND indexname IN (
                          'idx_trust_score_intervention_rule_enabled',
                          'idx_trust_score_intervention_rule_trigger'
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_name = 'place_information_evidence'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM information_schema.columns
                    WHERE table_name = 'map_place'
                      AND (
                          (column_name IN ('primary_information_source', 'information_verification_status')
                              AND data_type = 'character varying'
                              AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name IN ('information_verified_at', 'information_evidence_updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'YES')
                          OR (column_name = 'information_verified_by_admin_user_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'map_place'::regclass
                      AND conname IN (
                          'ck_map_place_primary_information_source',
                          'ck_map_place_information_verification_status',
                          'ck_map_place_information_admin_verified_metadata'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 15
                    FROM information_schema.columns
                    WHERE table_name = 'place_information_evidence'
                      AND (
                          (column_name = 'place_information_evidence_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'map_place_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('source_type', 'evidence_type', 'verification_status')
                              AND data_type = 'character varying'
                              AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name = 'external_reference'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 100
                              AND is_nullable = 'YES')
                          OR (column_name = 'reference_url'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name = 'description'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 1000
                              AND is_nullable = 'YES')
                          OR (column_name IN ('submitted_by_user_id', 'reviewed_by_admin_user_id')
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name = 'review_reason'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name IN ('submitted_at', 'created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'NO')
                          OR (column_name = 'reviewed_at'
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'YES')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM pg_constraint
                    WHERE conrelid = 'place_information_evidence'::regclass
                      AND conname IN (
                          'ck_place_information_evidence_source_type',
                          'ck_place_information_evidence_type',
                          'ck_place_information_evidence_status',
                          'ck_place_information_evidence_payload',
                          'ck_place_information_evidence_review'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_information_evidence'::regclass
                          AND conname = 'fk_place_information_evidence_place'
                          AND contype = 'f'
                          AND confrelid = 'map_place'::regclass
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_indexes
                    WHERE indexname IN (
                        'idx_place_information_evidence_place_status',
                        'idx_place_information_evidence_source',
                        'idx_map_place_information_verification'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 16
                    FROM information_schema.columns
                    WHERE table_name = 'place_information_report'
                      AND (
                          (column_name = 'place_information_report_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('map_place_id', 'reporter_user_id')
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'place_information_evidence_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name IN ('target_type', 'reason_type', 'status')
                              AND data_type = 'character varying'
                              AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name = 'description'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 1000
                              AND is_nullable = 'NO')
                          OR (column_name = 'evidence_url'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name = 'reviewed_by_admin_user_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name = 'review_reason'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name IN ('reviewed_at', 'resolved_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'YES')
                          OR (column_name IN ('created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'NO')
                          OR (column_name = 'version'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 6
                    FROM pg_constraint
                    WHERE conrelid = 'place_information_report'::regclass
                      AND conname IN (
                          'ck_place_information_report_target',
                          'ck_place_information_report_reason',
                          'ck_place_information_report_status',
                          'ck_place_information_report_description',
                          'ck_place_information_report_review_metadata',
                          'ck_place_information_report_resolved_metadata'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'place_information_report'::regclass
                      AND conname IN (
                          'fk_place_information_report_place',
                          'fk_place_information_report_evidence'
                      )
                      AND contype = 'f'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 5
                    FROM pg_indexes
                    WHERE indexname IN (
                        'uq_place_information_report_active',
                        'idx_place_information_report_place_status',
                        'idx_place_information_report_reporter_created',
                        'idx_place_information_report_status_created',
                        'idx_place_information_report_evidence'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 12
                    FROM information_schema.columns
                    WHERE table_name = 'place_information_report_dispute'
                      AND (
                          (column_name = 'place_information_report_dispute_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('place_information_report_id', 'disputed_by_user_id')
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'description'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 1000
                              AND is_nullable = 'NO')
                          OR (column_name = 'evidence_url'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name = 'status'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 30
                              AND is_nullable = 'NO')
                          OR (column_name = 'reviewed_by_admin_user_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name = 'review_reason'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500
                              AND is_nullable = 'YES')
                          OR (column_name = 'reviewed_at'
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'YES')
                          OR (column_name IN ('created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'NO')
                          OR (column_name = 'version'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_constraint
                    WHERE conrelid = 'place_information_report_dispute'::regclass
                      AND conname IN (
                          'ck_place_information_report_dispute_status',
                          'ck_place_information_report_dispute_description',
                          'ck_place_information_report_dispute_review'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_indexes
                    WHERE indexname IN (
                        'idx_place_information_report_dispute_report_status',
                        'idx_place_information_report_dispute_user_created',
                        'place_information_report_dispute_pkey'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 11
                    FROM information_schema.columns
                    WHERE table_name = 'place_media'
                      AND (
                          (column_name = 'place_media_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'map_place_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'NO')
                          OR (column_name = 'purpose'
                              AND data_type = 'character varying'
                              AND character_maximum_length = 20
                              AND is_nullable = 'NO')
                          OR (column_name IN ('image_url', 's3_key', 'thumbnail_url', 'thumbnail_s3_key')
                              AND data_type = 'character varying'
                              AND character_maximum_length = 500)
                          OR (column_name = 'source_map_image_id'
                              AND data_type = 'bigint'
                              AND is_nullable = 'YES')
                          OR (column_name = 'display_order'
                              AND data_type = 'integer'
                              AND is_nullable = 'NO')
                          OR (column_name IN ('created_at', 'updated_at')
                              AND data_type = 'timestamp without time zone'
                              AND is_nullable = 'NO')
                      )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 4
                    FROM pg_constraint
                    WHERE conrelid = 'place_media'::regclass
                      AND conname IN (
                          'ck_place_media_purpose',
                          'ck_place_media_image_url',
                          'ck_place_media_source',
                          'ck_place_media_display_order'
                      )
                      AND contype = 'c'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_constraint
                    WHERE conrelid = 'place_media'::regclass
                      AND conname IN (
                          'fk_place_media_place',
                          'fk_place_media_source_map_image'
                      )
                      AND contype = 'f'
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conrelid = 'place_media'::regclass
                          AND conname = 'fk_place_media_source_map_image'
                          AND confdeltype = 'c'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 3
                    FROM pg_indexes
                    WHERE indexname IN (
                        'uq_place_media_source_map_image',
                        'uq_place_media_primary_exploration',
                        'idx_place_media_place_purpose_order'
                    )
                    """)).isTrue();
            assertThat(queryBoolean(statement, """
                    SELECT COUNT(*) = 2
                    FROM pg_indexes
                    WHERE tablename = 'map_place'
                      AND indexname IN (
                          'idx_map_place_location_gist',
                          'idx_map_place_location_geography_gist'
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
