package com.typenull.pingdom.moderation.application.service.place.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlaceMergeSnapshotCodecTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final PlaceMergeSnapshotCodec codec = new PlaceMergeSnapshotCodec(objectMapper);

    @Test
    void capturesExactlyTheExistingSnapshotContract() throws Exception {
        MapPlace place = MapPlace.builder()
                .id(101L)
                .name("복구 장소")
                .address("서울시 종로구 복구로 10")
                .roadAddress("서울시 종로구 복구로 10")
                .jibunAddress("서울시 종로구 복구동 1")
                .postalCode("03000")
                .geocodingSource(GeocodingSource.ADMIN)
                .operatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED)
                .operatingStatusCheckedAt(LocalDateTime.of(2026, 8, 1, 10, 20, 30))
                .category("문화시설")
                .imageUrl("https://example.com/place.jpg")
                .kakaoPlaceId("kakao-101")
                .latitude(37.57)
                .longitude(126.98)
                .userId(17L)
                .registrant("등록자")
                .photoCount(3L)
                .description("스냅샷에 포함하지 않는 설명")
                .communityViewCount(99L)
                .build();
        place.updateTouristInformation("Restored Place", "관광 정보", Set.of(TouristCategory.EXHIBITION));
        place.replaceOperatingSchedule(
                Set.of(PlaceRegularOperatingHour.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))),
                List.of(PlaceOperatingException.customHours(place, LocalDate.of(2026, 8, 15),
                        Set.of(PlaceOperatingTimeRange.of(LocalTime.of(10, 0), LocalTime.of(16, 0)))))
        );

        JsonNode encoded = objectMapper.readTree(codec.write(codec.capture(place)));

        assertThat(encoded).isEqualTo(fixture());
        assertThat(encoded.has("description")).isFalse();
        assertThat(encoded.has("communityViewCount")).isFalse();
        assertThat(encoded.has("regularOperatingBreakTimes")).isFalse();
    }

    @Test
    void readsAndWritesTheUnversionedLegacyFixtureWithoutChangingFields() throws Exception {
        JsonNode legacy = fixture();

        JsonNode encoded = objectMapper.readTree(codec.write(codec.read(legacy.toString())));

        assertThat(encoded).isEqualTo(legacy);
    }

    @Test
    void retainsMissingLegacyFieldsForTheExistingRestoreDefaults() throws Exception {
        ObjectNode legacy = (ObjectNode) fixture();
        legacy.remove(List.of("photoCount", "geocodingSource", "operatingStatus", "regularOperatingHours", "operatingExceptions"));

        var snapshot = codec.read(legacy.toString());

        assertThat(snapshot.photoCount()).isNull();
        assertThat(snapshot.geocodingSource()).isNull();
        assertThat(snapshot.operatingStatus()).isNull();
        assertThat(snapshot.regularOperatingHours()).isNull();
        assertThat(snapshot.operatingExceptions()).isNull();
        assertThat(snapshot.id()).isEqualTo(101L);
    }

    @Test
    void retainsTheInjectedMapperUnknownFieldPolicy() throws Exception {
        ObjectNode legacy = (ObjectNode) fixture();
        legacy.put("futureField", "ignored by the configured mapper");
        assertThat(codec.read(legacy.toString()).id()).isEqualTo(101L);

        ObjectMapper strictMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        PlaceMergeSnapshotCodec strictCodec = new PlaceMergeSnapshotCodec(strictMapper);
        assertThatThrownBy(() -> strictCodec.read(legacy.toString()))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
    }

    @Test
    void mapsMalformedJsonAndUnknownEnumsToTheExistingRestoreError() throws Exception {
        assertThatThrownBy(() -> codec.read("{"))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        ObjectNode legacy = (ObjectNode) fixture();
        legacy.put("operatingStatus", "UNKNOWN_STATUS");
        assertThatThrownBy(() -> codec.read(legacy.toString()))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/moderation/place-merge-snapshot-v1.json"))) {
            return objectMapper.readTree(input);
        }
    }
}
