package com.typenull.pingdom.moderation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PlaceMergeRestoreRepositoryTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PlaceMergeRestoreRepository repository = new PlaceMergeRestoreRepository(jdbcTemplate);
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final LocalDateTime restoredAt = LocalDateTime.of(2026, 11, 12, 12, 30);

    @Test
    void mapsSnapshotFieldsToTheExistingInsertColumns() throws Exception {
        repository.insert(snapshot(false), restoredAt);

        Map<String, Object> columns = insertedColumns();
        assertThat(columns).hasSize(23)
                .containsEntry("map_place_id", 101L)
                .containsEntry("place_name", "복구 장소")
                .containsEntry("address", "서울시 종로구 복구로 10")
                .containsEntry("road_address", "서울시 종로구 복구로 10")
                .containsEntry("jibun_address", "서울시 종로구 복구동 1")
                .containsEntry("postal_code", "03000")
                .containsEntry("geocoding_source", "ADMIN")
                .containsEntry("operating_status", "TEMPORARILY_CLOSED")
                .containsEntry("operating_status_checked_at", LocalDateTime.of(2026, 8, 1, 10, 20, 30))
                .containsEntry("category", "문화시설")
                .containsEntry("image_url", "https://example.com/place.jpg")
                .containsEntry("kakao_place_id", "kakao-101")
                .containsEntry("latitude", 37.57)
                .containsEntry("longitude", 126.98)
                .containsEntry("user_id", 17L)
                .containsEntry("registrant", "등록자")
                .containsEntry("photo_count", 3L)
                .containsEntry("english_name", "Restored Place")
                .containsEntry("tourist_summary", "관광 정보")
                .containsEntry("discovery_status", "VISIBLE")
                .containsEntry("primary_information_source", "LEGACY")
                .containsEntry("information_verification_status", "UNVERIFIED")
                .containsEntry("created_at", restoredAt);
        assertThat(columns).doesNotContainKeys("description", "community_view_count");
    }

    @Test
    void keepsDefaultsForMissingLegacyValues() throws Exception {
        repository.insert(snapshot(true), restoredAt);

        assertThat(insertedColumns()).containsEntry("geocoding_source", "LEGACY")
                .containsEntry("operating_status", "OPERATING")
                .containsEntry("photo_count", 0L);
    }

    @Test
    void propagatesPersistenceFailureToTheCallingRestoreTransaction() throws Exception {
        PlaceMergeSnapshot snapshot = snapshot(false);
        var failure = new DataIntegrityViolationException("constraint violation");
        doThrow(failure).when(jdbcTemplate).update(anyString(), any(Object[].class));

        assertThatThrownBy(() -> repository.insert(snapshot, restoredAt)).isSameAs(failure);
    }

    private Map<String, Object> insertedColumns() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue().stripLeading()).startsWith("INSERT INTO map_place (");
        String[] columns = sql.getValue()
                .substring(sql.getValue().indexOf('(') + 1, sql.getValue().indexOf(')')).split(",");
        assertThat(arguments.getValue()).hasSize(columns.length);
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < columns.length; index++) {
            result.put(columns[index].trim(), arguments.getValue()[index]);
        }
        return result;
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
