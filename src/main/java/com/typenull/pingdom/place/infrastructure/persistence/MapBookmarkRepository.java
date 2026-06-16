package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.MapBookmark;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapBookmarkRepository extends JpaRepository<MapBookmark, Long> {

    interface PlaceBookmarkCountProjection {
        Long getPlaceId();

        long getBookmarkCount();
    }

    interface PlaceBookmarkUserProjection {
        Long getPlaceId();

        Long getUserId();
    }

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    long countByPlaceId(Long placeId);

    void deleteByPlaceIdAndUserId(Long placeId, Long userId);

    @Query("""
            SELECT b.placeId
            FROM MapBookmark b
            WHERE b.userId = :userId
            """)
    List<Long> findPlaceIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT b.placeId as placeId, COUNT(b) as bookmarkCount
            FROM MapBookmark b
            WHERE b.placeId IN :placeIds
            GROUP BY b.placeId
            """)
    List<PlaceBookmarkCountProjection> findBookmarkCountsByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT b.placeId as placeId, b.userId as userId
            FROM MapBookmark b
            WHERE b.placeId IN :placeIds
            """)
    List<PlaceBookmarkUserProjection> findBookmarkUsersByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT COUNT(DISTINCT b.userId)
            FROM MapBookmark b
            """)
    long countDistinctUserId();
}
