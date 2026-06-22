package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import java.util.List;
import java.util.Optional;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);

    @Modifying
    @Query("""
            UPDATE MapPlace m
            SET m.registrant = :displayName
            WHERE m.userId = :userId
            """)
    int updateRegistrantByUserId(
            @Param("userId") Long userId,
            @Param("displayName") String displayName
    );

    @Modifying
    @Query("""
            UPDATE MapPlace m
            SET m.userId = NULL
            WHERE m.userId IN :userIds
            """)
    int clearUserIdByUserIds(@Param("userIds") java.util.Collection<Long> userIds);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    List<MapPlace> findAllByNameAndAddress(String name, String address);

    Optional<MapPlace> findFirstByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

    boolean existsByNameAndAddressAndLatitudeAndLongitude(
            String name,
            String address,
            Double latitude,
            Double longitude
    );

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
                       AND TRIM(m.kakaoPlaceId) <> ''
                       AND other.kakaoPlaceId IS NOT NULL
                       AND TRIM(other.kakaoPlaceId) = TRIM(m.kakaoPlaceId))
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
              AND TRIM(m.kakaoPlaceId) <> ''
              AND TRIM(m.kakaoPlaceId) = TRIM(:kakaoPlaceId)
            """)
    List<MapPlace> findDuplicateCandidatesByKakaoPlaceId(
            @Param("placeId") Long placeId,
            @Param("kakaoPlaceId") String kakaoPlaceId
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
            """)
    List<MapPlace> findAllWithCoordinates(Pageable pageable);

    @Query(
            value = """
                    SELECT m
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    ORDER BY m.latitude ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(m)
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    """
    )
    Page<MapPlace> findCoordinatePage(Pageable pageable);

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND m.longitude BETWEEN :minLongitude AND :maxLongitude
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180d THEN ABS(m.longitude - :longitude)
                         ELSE 360d - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInBoundingBox(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180d THEN ABS(m.longitude - :longitude)
                         ELSE 360d - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInLatitudeBand(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            Pageable pageable
    );

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND m.latitude BETWEEN :minLatitude AND :maxLatitude
              AND (m.longitude >= :westLongitude OR m.longitude <= :eastLongitude)
            ORDER BY ABS(m.latitude - :latitude)
                   + CASE
                         WHEN ABS(m.longitude - :longitude) <= 180d THEN ABS(m.longitude - :longitude)
                         ELSE 360d - ABS(m.longitude - :longitude)
                     END
            """)
    List<MapPlace> findRecommendationCandidatesInWrappedLongitudeBoundingBox(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("westLongitude") double westLongitude,
            @Param("eastLongitude") double eastLongitude,
            Pageable pageable
    );

    @Query("SELECT m FROM MapPlace m WHERE (:keyword IS NULL OR :keyword = '' OR m.name LIKE %:keyword%)")
    Page<MapPlace> findByNameContaining(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    @Query(
            value = """
                    SELECT
                        mp.map_place_id AS id,
                        mp.place_name AS name,
                        mp.address AS address,
                        mp.category AS category,
                        mp.latitude AS latitude,
                        mp.longitude AS longitude,
                        CASE
                            WHEN :hasLocation = TRUE THEN
                                6371000.0 * 2.0 * ASIN(SQRT(
                                    POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                    + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                    * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                ))
                            ELSE NULL
                        END AS distanceMeters
                    FROM map_place mp
                    WHERE (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR LOWER(TRIM(mp.category)) = :category)
                      AND (
                          :hasLocation = FALSE
                          OR (
                              mp.latitude BETWEEN :minLatitude AND :maxLatitude
                              AND (
                                  (:longitudeWrapped = FALSE AND mp.longitude BETWEEN :westLongitude AND :eastLongitude)
                                  OR (:longitudeWrapped = TRUE AND (mp.longitude >= :westLongitude OR mp.longitude <= :eastLongitude))
                              )
                              AND (
                                  6371000.0 * 2.0 * ASIN(SQRT(
                                      POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                      + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                      * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                  ))
                              ) <= :radiusMeters
                          )
                      )
                    ORDER BY
                        CASE
                            WHEN :sort = 'NEAREST' THEN
                                6371000.0 * 2.0 * ASIN(SQRT(
                                    POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                    + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                    * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                ))
                            ELSE NULL
                        END ASC,
                        CASE WHEN :sort = 'LATEST' THEN mp.map_place_id ELSE NULL END DESC,
                        mp.map_place_id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM map_place mp
                    WHERE (:keywordPattern IS NULL
                           OR LOWER(mp.place_name) LIKE :keywordPattern ESCAPE '\\'
                           OR LOWER(mp.address) LIKE :keywordPattern ESCAPE '\\')
                      AND (:category IS NULL OR LOWER(TRIM(mp.category)) = :category)
                      AND (
                          :hasLocation = FALSE
                          OR (
                              mp.latitude BETWEEN :minLatitude AND :maxLatitude
                              AND (
                                  (:longitudeWrapped = FALSE AND mp.longitude BETWEEN :westLongitude AND :eastLongitude)
                                  OR (:longitudeWrapped = TRUE AND (mp.longitude >= :westLongitude OR mp.longitude <= :eastLongitude))
                              )
                              AND (
                                  6371000.0 * 2.0 * ASIN(SQRT(
                                      POWER(SIN(RADIANS(mp.latitude - :latitude) / 2.0), 2.0)
                                      + COS(RADIANS(:latitude)) * COS(RADIANS(mp.latitude))
                                      * POWER(SIN(RADIANS(mp.longitude - :longitude) / 2.0), 2.0)
                                  ))
                              ) <= :radiusMeters
                          )
                      )
                    """,
            nativeQuery = true
    )
    Page<PlaceSearchProjection> searchPlaces(
            @Param("keywordPattern") String keywordPattern,
            @Param("category") String category,
            @Param("hasLocation") boolean hasLocation,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") Double radiusMeters,
            @Param("minLatitude") Double minLatitude,
            @Param("maxLatitude") Double maxLatitude,
            @Param("westLongitude") Double westLongitude,
            @Param("eastLongitude") Double eastLongitude,
            @Param("longitudeWrapped") boolean longitudeWrapped,
            @Param("sort") String sort,
            Pageable pageable
    );

    interface PlaceSearchProjection {
        Long getId();

        String getName();

        String getAddress();

        String getCategory();

        Double getLatitude();

        Double getLongitude();

        Double getDistanceMeters();
    }

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR m.name LIKE CONCAT('%', :keyword, '%')
                   OR m.address LIKE CONCAT('%', :keyword, '%')
                   OR (:numericKeyword IS NOT NULL AND m.userId = :numericKeyword))
            """)
    Page<MapPlace> searchAdminPlaces(
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT p
                    FROM MapBookmark b
                    JOIN MapPlace p ON p.id = b.placeId
                    WHERE b.userId = :userId
                    ORDER BY b.createdAt DESC, b.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(b)
                    FROM MapBookmark b
                    WHERE b.userId = :userId
                    """
    )
    Page<MapPlace> findBookmarkedPlacesByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderBySmoothedCtr(
            @Param("keyword") String keyword,
            @Param("globalCtr") double globalCtr,
            @Param("priorWeight") double priorWeight,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByRawCtr(
            @Param("keyword") String keyword,
            @Param("globalCtr") double globalCtr,
            @Param("priorWeight") double priorWeight,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.bookmarkConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.bookmarkConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByBookmarkConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.likeConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.likeConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByLikeConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE ((COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) * 1.0)
                              / COALESCE(s.exposureCount, 0)
                     END DESC,
                     (COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByTotalConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY COALESCE(s.exposureCount, 0) DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByExposure(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY COALESCE(s.clickCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByClick(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE WHEN s.updatedAt IS NULL THEN 1 ELSE 0 END ASC,
                     s.updatedAt DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByUpdatedAt(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderBySmoothedCtr(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("globalCtr") double globalCtr,
            @Param("priorWeight") double priorWeight,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByRawCtr(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("globalCtr") double globalCtr,
            @Param("priorWeight") double priorWeight,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.bookmarkConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.bookmarkConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByBookmarkConversion(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE (COALESCE(s.likeConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.likeConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByLikeConversion(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0d
                         ELSE ((COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) * 1.0)
                              / COALESCE(s.exposureCount, 0)
                     END DESC,
                     (COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByTotalConversion(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY COALESCE(s.exposureCount, 0) DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByExposure(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY COALESCE(s.clickCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByClick(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationVersionSnapshot s
                   ON s.placeId = p.id
                  AND s.recommendationVersion = :recommendationVersion
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            ORDER BY CASE WHEN s.updatedAt IS NULL THEN 1 ELSE 0 END ASC,
                     s.updatedAt DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword%)
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByUpdatedAt(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MapPlace m WHERE m.id = :placeId")
    Optional<MapPlace> findByIdForUpdate(@Param("placeId") Long placeId);
}
