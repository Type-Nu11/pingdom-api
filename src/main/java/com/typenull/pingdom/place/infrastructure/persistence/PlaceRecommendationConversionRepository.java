package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationConversionRepository extends JpaRepository<PlaceRecommendationConversion, Long> {

    interface PlaceConversionCountProjection {
        Long getPlaceId();

        PlaceRecommendationConversionType getConversionType();

        long getConversionCount();
    }

    interface PlaceVersionConversionCountProjection {
        Long getPlaceId();

        String getRecommendationVersion();

        PlaceRecommendationConversionType getConversionType();

        long getConversionCount();
    }

    boolean existsByUserIdAndPlaceIdAndConversionType(
            Long userId,
            Long placeId,
            PlaceRecommendationConversionType conversionType
    );

    @Query("""
            SELECT c.placeId as placeId,
                   c.conversionType as conversionType,
                   COUNT(c) as conversionCount
            FROM PlaceRecommendationConversion c
            WHERE c.placeId IN :placeIds
            GROUP BY c.placeId, c.conversionType
            """)
    List<PlaceConversionCountProjection> countConversionsByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT c.placeId as placeId,
                   c.conversionType as conversionType,
                   COUNT(c) as conversionCount
            FROM PlaceRecommendationConversion c
            WHERE c.placeId IN :placeIds
              AND c.recommendationVersion = :recommendationVersion
            GROUP BY c.placeId, c.conversionType
            """)
    List<PlaceConversionCountProjection> countConversionsByPlaceIdsAndRecommendationVersion(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion
    );

    @Query("""
            SELECT c.placeId as placeId,
                   c.conversionType as conversionType,
                   COUNT(c) as conversionCount
            FROM PlaceRecommendationConversion c
            WHERE c.placeId IN :placeIds
              AND c.createdAt >= :cutoff
            GROUP BY c.placeId, c.conversionType
            """)
    List<PlaceConversionCountProjection> countConversionsByPlaceIdsAndCreatedAtGreaterThanEqual(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
            SELECT c.placeId as placeId,
                   c.conversionType as conversionType,
                   COUNT(c) as conversionCount
            FROM PlaceRecommendationConversion c
            WHERE c.placeId IN :placeIds
              AND c.recommendationVersion = :recommendationVersion
              AND c.createdAt >= :cutoff
            GROUP BY c.placeId, c.conversionType
            """)
    List<PlaceConversionCountProjection> countConversionsByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
            SELECT c.placeId as placeId,
                   c.recommendationVersion as recommendationVersion,
                   c.conversionType as conversionType,
                   COUNT(c) as conversionCount
            FROM PlaceRecommendationConversion c
            GROUP BY c.placeId, c.recommendationVersion, c.conversionType
            """)
    List<PlaceVersionConversionCountProjection> countConversionsGroupedByPlaceIdAndRecommendationVersion();
}
