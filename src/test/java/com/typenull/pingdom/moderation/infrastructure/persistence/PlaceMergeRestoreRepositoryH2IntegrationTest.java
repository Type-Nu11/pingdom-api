package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
class PlaceMergeRestoreRepositoryH2IntegrationTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final LocalDateTime restoredAt = LocalDateTime.of(2026, 11, 12, 12, 30);
    private JdbcTemplate jdbcTemplate;
    private PlaceMergeRestoreRepository repository;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:place-merge-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PlaceMergeRestoreRepository(jdbcTemplate);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbcTemplate.execute("""
                CREATE TABLE map_place (
                    map_place_id BIGINT PRIMARY KEY, place_name VARCHAR(100), address VARCHAR(255),
                    road_address VARCHAR(255), jibun_address VARCHAR(255), postal_code VARCHAR(20),
                    geocoding_source VARCHAR(20), operating_status VARCHAR(30), operating_status_checked_at TIMESTAMP,
                    category VARCHAR(50), image_url VARCHAR(500), kakao_place_id VARCHAR(50),
                    latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, user_id BIGINT, registrant VARCHAR(100),
                    photo_count BIGINT, english_name VARCHAR(150), tourist_summary VARCHAR(500),
                    discovery_status VARCHAR(20), primary_information_source VARCHAR(30),
                    information_verification_status VARCHAR(30), created_at TIMESTAMP,
                    description VARCHAR(1000), community_view_count BIGINT DEFAULT 0
                )
                """);
    }

    @AfterEach
    void closeDatabase() {
        if (jdbcTemplate != null) jdbcTemplate.execute("SHUTDOWN");
    }

    @Test
    void mapsLegacySnapshotColumnsAndKeepsTheLimitedRestoreDefaults() throws Exception {
        PlaceMergeSnapshot snapshot = snapshot(false);

        repository.insert(snapshot, restoredAt);

        var row = jdbcTemplate.queryForMap("SELECT * FROM map_place WHERE map_place_id = 101");
        assertThat(row).containsEntry("MAP_PLACE_ID", 101L)
                .containsEntry("PLACE_NAME", "복구 장소")
                .containsEntry("ADDRESS", "서울시 종로구 복구로 10")
                .containsEntry("ROAD_ADDRESS", "서울시 종로구 복구로 10")
                .containsEntry("JIBUN_ADDRESS", "서울시 종로구 복구동 1")
                .containsEntry("POSTAL_CODE", "03000")
                .containsEntry("GEOCODING_SOURCE", "ADMIN")
                .containsEntry("OPERATING_STATUS", "TEMPORARILY_CLOSED")
                .containsEntry("OPERATING_STATUS_CHECKED_AT", Timestamp.valueOf("2026-08-01 10:20:30"))
                .containsEntry("CATEGORY", "문화시설")
                .containsEntry("IMAGE_URL", "https://example.com/place.jpg")
                .containsEntry("KAKAO_PLACE_ID", "kakao-101")
                .containsEntry("LATITUDE", 37.57)
                .containsEntry("LONGITUDE", 126.98)
                .containsEntry("USER_ID", 17L)
                .containsEntry("REGISTRANT", "등록자")
                .containsEntry("PHOTO_COUNT", 3L)
                .containsEntry("ENGLISH_NAME", "Restored Place")
                .containsEntry("TOURIST_SUMMARY", "관광 정보")
                .containsEntry("DISCOVERY_STATUS", "VISIBLE")
                .containsEntry("PRIMARY_INFORMATION_SOURCE", "LEGACY")
                .containsEntry("INFORMATION_VERIFICATION_STATUS", "UNVERIFIED")
                .containsEntry("CREATED_AT", Timestamp.valueOf(restoredAt))
                .containsEntry("DESCRIPTION", null)
                .containsEntry("COMMUNITY_VIEW_COUNT", 0L);
    }

    @Test
    void appliesExistingDefaultsWhenLegacyValuesAreMissing() throws Exception {
        repository.insert(snapshot(true), restoredAt);

        var row = jdbcTemplate.queryForMap("SELECT * FROM map_place WHERE map_place_id = 101");
        assertThat(row).containsEntry("GEOCODING_SOURCE", "LEGACY")
                .containsEntry("OPERATING_STATUS", "OPERATING")
                .containsEntry("PHOTO_COUNT", 0L);
    }

    @Test
    void participatesInTheCallersTransactionWhenLaterRestoreWorkFails() throws Exception {
        PlaceMergeSnapshot snapshot = snapshot(false);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.insert(snapshot, restoredAt);
            throw new IllegalStateException("failure after insert");
        })).isInstanceOf(IllegalStateException.class).hasMessage("failure after insert");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM map_place", Long.class)).isZero();
    }

    private PlaceMergeSnapshot snapshot(boolean legacyMissingValues) throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/moderation/place-merge-snapshot-v1.json"))) {
            ObjectNode fixture = (ObjectNode) objectMapper.readTree(input);
            if (legacyMissingValues) fixture.remove(List.of("geocodingSource", "operatingStatus", "photoCount"));
            return objectMapper.treeToValue(fixture, PlaceMergeSnapshot.class);
        }
    }
}
