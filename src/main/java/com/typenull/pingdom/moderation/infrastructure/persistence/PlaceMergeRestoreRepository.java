package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 명시 ID 복원 INSERT만 담당하며 호출자의 병합 복구 트랜잭션에 참여. 파생 필드 보정은 application에서 수행. */
@Repository
@RequiredArgsConstructor
public class PlaceMergeRestoreRepository {
    private final JdbcTemplate jdbcTemplate;

    public void insert(PlaceMergeSnapshot snapshot, LocalDateTime restoredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO map_place (
                    map_place_id, place_name, address, road_address, jibun_address, postal_code, geocoding_source,
                    operating_status, operating_status_checked_at, category, image_url, kakao_place_id,
                    latitude, longitude, user_id, registrant, photo_count, english_name, tourist_summary,
                    discovery_status, primary_information_source, information_verification_status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                snapshot.name(),
                snapshot.address(),
                snapshot.roadAddress(),
                snapshot.jibunAddress(),
                snapshot.postalCode(),
                (snapshot.geocodingSource() == null ? GeocodingSource.LEGACY : snapshot.geocodingSource()).name(),
                (snapshot.operatingStatus() == null ? PlaceOperatingStatus.OPERATING : snapshot.operatingStatus()).name(),
                snapshot.operatingStatusCheckedAt(),
                snapshot.category(),
                snapshot.imageUrl(),
                snapshot.kakaoPlaceId(),
                snapshot.latitude(),
                snapshot.longitude(),
                snapshot.userId(),
                snapshot.registrant(),
                snapshot.photoCount() == null ? 0L : snapshot.photoCount(),
                snapshot.englishName(),
                snapshot.touristSummary(),
                PlaceDiscoveryStatus.VISIBLE.name(),
                PlaceInformationSourceType.LEGACY.name(),
                PlaceInformationVerificationStatus.UNVERIFIED.name(),
                restoredAt
        );
    }
}
