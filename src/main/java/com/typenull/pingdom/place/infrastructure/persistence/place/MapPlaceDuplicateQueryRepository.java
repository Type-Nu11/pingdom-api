package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MapPlaceDuplicateQueryRepository extends Repository<MapPlace, Long> {

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.id <> :placeId
              AND m.name = :name
              AND m.address = :address
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND m.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<MapPlace> findDuplicateCandidatesByNameAndAddressInBoundingBox(
            @Param("placeId") Long placeId,
            @Param("name") String name,
            @Param("address") String address,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE EXISTS (
                SELECT other.id
                FROM MapPlace other
                WHERE other.id <> m.id
                  AND (
                      (m.kakaoPlaceId IS NOT NULL
                       AND m.kakaoPlaceId <> ''
                       AND other.kakaoPlaceId IS NOT NULL
                       AND other.kakaoPlaceId <> ''
                       AND other.kakaoPlaceId = m.kakaoPlaceId)
                      OR (other.name = m.name AND other.address = m.address)
                  )
            )
            """)
    List<MapPlace> findPotentialDuplicatePlaces();

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.id <> :placeId
              AND m.kakaoPlaceId IS NOT NULL
              AND m.kakaoPlaceId <> ''
              AND m.kakaoPlaceId = :kakaoPlaceId
            """)
    List<MapPlace> findDuplicateCandidatesByKakaoPlaceId(
            @Param("placeId") Long placeId,
            @Param("kakaoPlaceId") String kakaoPlaceId
    );
}
