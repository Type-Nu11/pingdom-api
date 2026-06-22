package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminPlaceRecommendationMetricRepository extends Repository<MapPlace, Long> {

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
}
