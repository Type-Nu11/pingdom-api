package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminPlaceRecommendationMetricRepository extends Repository<MapPlace, Long> {

    interface PeriodMetricCountProjection {
        Long getExposureCount();

        Long getClickCount();
    }

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.bookmarkConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.bookmarkConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByBookmarkConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.likeConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.likeConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByLikeConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE ((COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) * 1.0)
                              / COALESCE(s.exposureCount, 0)
                     END DESC,
                     (COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByTotalConversion(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY COALESCE(s.exposureCount, 0) DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByExposure(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY COALESCE(s.clickCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findRecommendationMetricPageOrderByClick(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value = """
            SELECT p
            FROM MapPlace p
            LEFT JOIN PlaceRecommendationSnapshot s ON s.placeId = p.id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE WHEN s.updatedAt IS NULL THEN 1 ELSE 0 END ASC,
                     s.updatedAt DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.clickCount, 0) + (:priorWeight * :globalCtr))
                              / (COALESCE(s.exposureCount, 0) + :priorWeight)
                     END DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.bookmarkConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.bookmarkConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE (COALESCE(s.likeConversionCount, 0) * 1.0) / COALESCE(s.exposureCount, 0)
                     END DESC,
                     COALESCE(s.likeConversionCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE
                         WHEN COALESCE(s.exposureCount, 0) <= 0 THEN 0.0
                         ELSE ((COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) * 1.0)
                              / COALESCE(s.exposureCount, 0)
                     END DESC,
                     (COALESCE(s.bookmarkConversionCount, 0) + COALESCE(s.likeConversionCount, 0)) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY COALESCE(s.exposureCount, 0) DESC,
                     COALESCE(s.clickCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY COALESCE(s.clickCount, 0) DESC,
                     COALESCE(s.exposureCount, 0) DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
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
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY CASE WHEN s.updatedAt IS NULL THEN 1 ELSE 0 END ASC,
                     s.updatedAt DESC,
                     p.id ASC
            """, countQuery = """
            SELECT COUNT(p)
            FROM MapPlace p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<MapPlace> findVersionRecommendationMetricPageOrderByUpdatedAt(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            Pageable pageable
    );

    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(e.exposure_count, 0)), 0) AS exposureCount,
                   COALESCE(SUM(COALESCE(c.click_count, 0)), 0) AS clickCount
            FROM map_place p
            LEFT JOIN (
                SELECT place_id, COUNT(*) AS exposure_count
                FROM place_recommendation_exposure
                WHERE created_at >= :cutoff
                  AND (:recommendationVersion = '' OR recommendation_version = :recommendationVersion)
                GROUP BY place_id
            ) e ON e.place_id = p.map_place_id
            LEFT JOIN (
                SELECT place_id, COUNT(*) AS click_count
                FROM place_recommendation_click
                WHERE created_at >= :cutoff
                  AND (:recommendationVersion = '' OR recommendation_version = :recommendationVersion)
                GROUP BY place_id
            ) c ON c.place_id = p.map_place_id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.place_name LIKE CONCAT('%', :keyword, '%'))
            """, nativeQuery = true)
    PeriodMetricCountProjection sumPeriodMetricCounts(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query(value = """
            SELECT p.*
            FROM map_place p
            LEFT JOIN (
                SELECT place_id, COUNT(*) AS exposure_count
                FROM place_recommendation_exposure
                WHERE created_at >= :cutoff
                  AND (:recommendationVersion = '' OR recommendation_version = :recommendationVersion)
                GROUP BY place_id
            ) e ON e.place_id = p.map_place_id
            LEFT JOIN (
                SELECT place_id, COUNT(*) AS click_count
                FROM place_recommendation_click
                WHERE created_at >= :cutoff
                  AND (:recommendationVersion = '' OR recommendation_version = :recommendationVersion)
                GROUP BY place_id
            ) c ON c.place_id = p.map_place_id
            LEFT JOIN (
                SELECT place_id,
                       SUM(CASE WHEN conversion_type = 'BOOKMARK' THEN 1 ELSE 0 END) AS bookmark_conversion_count,
                       SUM(CASE WHEN conversion_type = 'LIKE' THEN 1 ELSE 0 END) AS like_conversion_count
                FROM place_recommendation_conversion
                WHERE created_at >= :cutoff
                  AND (:recommendationVersion = '' OR recommendation_version = :recommendationVersion)
                GROUP BY place_id
            ) v ON v.place_id = p.map_place_id
            WHERE (:keyword IS NULL OR :keyword = '' OR p.place_name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY
                CASE WHEN :sortBy = 'SMOOTHED_CTR' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(c.click_count, 0) + (:priorWeight * :globalCtr))
                             / (COALESCE(e.exposure_count, 0) + :priorWeight)
                    END
                END DESC,
                CASE WHEN :sortBy = 'SMOOTHED_CTR' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(c.click_count, 0) * 1.0) / COALESCE(e.exposure_count, 0)
                    END
                END DESC,
                CASE WHEN :sortBy = 'SMOOTHED_CTR' THEN COALESCE(c.click_count, 0) END DESC,
                CASE WHEN :sortBy = 'RAW_CTR' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(c.click_count, 0) * 1.0) / COALESCE(e.exposure_count, 0)
                    END
                END DESC,
                CASE WHEN :sortBy = 'RAW_CTR' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(c.click_count, 0) + (:priorWeight * :globalCtr))
                             / (COALESCE(e.exposure_count, 0) + :priorWeight)
                    END
                END DESC,
                CASE WHEN :sortBy = 'RAW_CTR' THEN COALESCE(e.exposure_count, 0) END DESC,
                CASE WHEN :sortBy = 'BOOKMARK_CONVERSION' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(v.bookmark_conversion_count, 0) * 1.0) / COALESCE(e.exposure_count, 0)
                    END
                END DESC,
                CASE WHEN :sortBy = 'BOOKMARK_CONVERSION' THEN COALESCE(v.bookmark_conversion_count, 0) END DESC,
                CASE WHEN :sortBy = 'BOOKMARK_CONVERSION' THEN COALESCE(e.exposure_count, 0) END DESC,
                CASE WHEN :sortBy = 'LIKE_CONVERSION' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE (COALESCE(v.like_conversion_count, 0) * 1.0) / COALESCE(e.exposure_count, 0)
                    END
                END DESC,
                CASE WHEN :sortBy = 'LIKE_CONVERSION' THEN COALESCE(v.like_conversion_count, 0) END DESC,
                CASE WHEN :sortBy = 'LIKE_CONVERSION' THEN COALESCE(e.exposure_count, 0) END DESC,
                CASE WHEN :sortBy = 'TOTAL_CONVERSION' THEN
                    CASE
                        WHEN COALESCE(e.exposure_count, 0) <= 0 THEN 0.0
                        ELSE ((COALESCE(v.bookmark_conversion_count, 0) + COALESCE(v.like_conversion_count, 0)) * 1.0)
                             / COALESCE(e.exposure_count, 0)
                    END
                END DESC,
                CASE WHEN :sortBy = 'TOTAL_CONVERSION' THEN
                    COALESCE(v.bookmark_conversion_count, 0) + COALESCE(v.like_conversion_count, 0)
                END DESC,
                CASE WHEN :sortBy = 'TOTAL_CONVERSION' THEN COALESCE(e.exposure_count, 0) END DESC,
                CASE WHEN :sortBy = 'EXPOSURE' THEN COALESCE(e.exposure_count, 0) END DESC,
                CASE WHEN :sortBy = 'EXPOSURE' THEN COALESCE(c.click_count, 0) END DESC,
                CASE WHEN :sortBy = 'CLICK' THEN COALESCE(c.click_count, 0) END DESC,
                CASE WHEN :sortBy = 'CLICK' THEN COALESCE(e.exposure_count, 0) END DESC,
                p.map_place_id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM map_place p
            WHERE (:keyword IS NULL OR :keyword = '' OR p.place_name LIKE CONCAT('%', :keyword, '%'))
            """, nativeQuery = true)
    Page<MapPlace> findPeriodRecommendationMetricPage(
            @Param("keyword") String keyword,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("sortBy") String sortBy,
            @Param("globalCtr") double globalCtr,
            @Param("priorWeight") double priorWeight,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );
}
