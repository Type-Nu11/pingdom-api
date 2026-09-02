package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface NearbyReservablePlaceQueryRepository extends Repository<MapPlace, Long> {

    @Query(value = """
            WITH reservable AS (
                SELECT mp.map_place_id AS placeId, mp.place_name AS name, mp.category AS category,
                       mp.latitude AS latitude, mp.longitude AS longitude, mp.address AS address, mp.image_url AS imageUrl,
                       mp.photo_count AS photoCount, a.id AS availabilityId, a.starts_at AS availableStartsAt,
                       a.ends_at AS availableEndsAt, a.remaining_capacity AS remainingCapacity, a.product_type AS productType,
                       a.product_id AS productId, product.name AS productName,
                       ST_Distance(mp.location::geography, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography) AS distanceMeters,
                       ROW_NUMBER() OVER (PARTITION BY mp.map_place_id ORDER BY a.starts_at ASC, a.id ASC) AS slotRank
                FROM map_place mp
                JOIN place_availability a ON a.place_id = mp.map_place_id
                LEFT JOIN reservable_product product ON product.id = a.product_id
                    AND product.place_id = mp.map_place_id AND product.product_type = a.product_type AND product.status = 'ACTIVE'
                WHERE mp.operating_status = 'OPERATING' AND mp.discovery_status = 'VISIBLE'
                  AND mp.location IS NOT NULL
                  AND ST_DWithin(mp.location::geography, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
                  AND a.status = 'ACTIVE' AND a.ends_at > :now AND a.ends_at > COALESCE(:fromAt, :now)
                  AND (:toAt IS NULL OR a.starts_at < :toAt) AND a.remaining_capacity >= :quantity
                  AND (:productType IS NULL OR a.product_type = :productType)
                  AND (a.product_type = 'GENERAL' OR product.id IS NOT NULL)
                  AND (:category IS NULL OR LOWER(TRIM(mp.category)) = :category)
                  AND (:touristCategory IS NULL OR EXISTS (SELECT 1 FROM map_place_tourist_category tc
                       WHERE tc.map_place_id = mp.map_place_id AND tc.tourist_category = :touristCategory))
                  AND EXISTS (SELECT 1 FROM merchant_owner_place op WHERE op.place_id = a.place_id
                       AND op.merchant_owner_user_id = a.merchant_owner_user_id)
                  AND EXISTS (SELECT 1 FROM merchant_owner_profile profile WHERE profile.user_id = a.merchant_owner_user_id
                       AND profile.status = 'ACTIVE')
                  AND EXISTS (SELECT 1 FROM merchant_verification verification WHERE verification.user_id = a.merchant_owner_user_id
                       AND verification.identity_status = 'APPROVED' AND verification.business_status = 'APPROVED')
                  AND EXISTS (SELECT 1 FROM users owner_user WHERE owner_user.id = a.merchant_owner_user_id
                       AND owner_user.role = 'MERCHANT_OWNER' AND owner_user.status = 'ACTIVE'
                       AND (owner_user.banned = FALSE OR (owner_user.ban_type = 'TEMPORARY' AND owner_user.ban_expires_at <= :now)))
            )
            SELECT * FROM reservable WHERE slotRank = 1
            ORDER BY CASE WHEN :sort = 'EARLIEST_AVAILABLE' THEN availableStartsAt END ASC,
                     CASE WHEN :sort = 'POPULAR' THEN photoCount END DESC,
                     CASE WHEN :sort = 'NEAREST' THEN distanceMeters END ASC,
                     placeId ASC
            """, countQuery = """
            SELECT COUNT(DISTINCT mp.map_place_id) FROM map_place mp
            JOIN place_availability a ON a.place_id = mp.map_place_id
            LEFT JOIN reservable_product product ON product.id = a.product_id
                AND product.place_id = mp.map_place_id AND product.product_type = a.product_type AND product.status = 'ACTIVE'
            WHERE mp.operating_status = 'OPERATING' AND mp.discovery_status = 'VISIBLE' AND mp.location IS NOT NULL
              AND ST_DWithin(mp.location::geography, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)
              AND a.status = 'ACTIVE' AND a.ends_at > :now AND a.ends_at > COALESCE(:fromAt, :now)
              AND (:toAt IS NULL OR a.starts_at < :toAt) AND a.remaining_capacity >= :quantity
              AND (:productType IS NULL OR a.product_type = :productType) AND (a.product_type = 'GENERAL' OR product.id IS NOT NULL)
              AND (:category IS NULL OR LOWER(TRIM(mp.category)) = :category)
              AND (:touristCategory IS NULL OR EXISTS (SELECT 1 FROM map_place_tourist_category tc WHERE tc.map_place_id = mp.map_place_id AND tc.tourist_category = :touristCategory))
              AND EXISTS (SELECT 1 FROM merchant_owner_place op WHERE op.place_id = a.place_id AND op.merchant_owner_user_id = a.merchant_owner_user_id)
              AND EXISTS (SELECT 1 FROM merchant_owner_profile profile WHERE profile.user_id = a.merchant_owner_user_id AND profile.status = 'ACTIVE')
              AND EXISTS (SELECT 1 FROM merchant_verification verification WHERE verification.user_id = a.merchant_owner_user_id AND verification.identity_status = 'APPROVED' AND verification.business_status = 'APPROVED')
              AND EXISTS (SELECT 1 FROM users owner_user WHERE owner_user.id = a.merchant_owner_user_id AND owner_user.role = 'MERCHANT_OWNER' AND owner_user.status = 'ACTIVE' AND (owner_user.banned = FALSE OR (owner_user.ban_type = 'TEMPORARY' AND owner_user.ban_expires_at <= :now)))
            """, nativeQuery = true)
    Page<NearbyReservablePlaceProjection> findNearbyReservablePlaces(
            @Param("latitude") double latitude, @Param("longitude") double longitude, @Param("radiusMeters") double radiusMeters,
            @Param("fromAt") LocalDateTime fromAt, @Param("toAt") LocalDateTime toAt, @Param("quantity") int quantity,
            @Param("productType") AvailabilityProductType productType, @Param("category") String category,
            @Param("touristCategory") String touristCategory, @Param("sort") String sort, @Param("now") LocalDateTime now,
            Pageable pageable);

    interface NearbyReservablePlaceProjection {
        Long getPlaceId(); String getName(); String getCategory(); Double getLatitude(); Double getLongitude(); String getAddress(); String getImageUrl();
        Double getDistanceMeters(); Long getAvailabilityId(); LocalDateTime getAvailableStartsAt(); LocalDateTime getAvailableEndsAt();
        Integer getRemainingCapacity(); AvailabilityProductType getProductType(); Long getProductId(); String getProductName();
    }
}
