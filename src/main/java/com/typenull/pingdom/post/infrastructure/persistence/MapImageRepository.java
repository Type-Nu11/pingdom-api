package com.typenull.pingdom.post.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MapImageRepository extends JpaRepository<MapImage,Long> {

    interface PlaceImageAggregateProjection {
        Long getPlaceId();

        Long getLikeSum();

        LocalDateTime getLatestCreatedAt();
    }

    @Modifying
    @Query("""
    UPDATE MapImage m
    SET m.likeCount = m.likeCount + 1
    WHERE m.id = :imageId
""")
    void increaseLikeCount(@Param("imageId") Long imageId);

    @Modifying
    @Query("""
    UPDATE MapImage m
    SET m.likeCount = m.likeCount - 1
    WHERE m.id = :imageId
    AND m.likeCount > 0
""")
    void decreaseLikeCount(@Param("imageId") Long imageId);

    @Modifying
    @Query("""
            UPDATE MapImage m
            SET m.username = :displayName
            WHERE m.userId = :userId
            """)
    int updateUsernameByUserId(
            @Param("userId") Long userId,
            @Param("displayName") String displayName
    );

    @Modifying
    @Query("""
            UPDATE MapImage m
            SET m.userId = NULL
            WHERE m.userId IN :userIds
            """)
    int clearUserIdByUserIds(@Param("userIds") Collection<Long> userIds);

    @EntityGraph(attributePaths = "mapPlace")
    Page<MapImage> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "mapPlace")
    Page<MapImage> findAllByVisibilityStatus(MapImageVisibilityStatus visibilityStatus, Pageable pageable);

    @EntityGraph(attributePaths = "mapPlace")
    @Query(
            value = """
                    SELECT m
                    FROM MapImage m
                    JOIN MapBookmark b ON b.placeId = m.mapPlace.id
                    WHERE b.userId = :userId
                      AND m.visibilityStatus = com.typenull.pingdom.post.domain.MapImageVisibilityStatus.ACTIVE
                      AND m.id = (
                          SELECT MAX(latest.id)
                          FROM MapImage latest
                          WHERE latest.mapPlace.id = m.mapPlace.id
                            AND latest.visibilityStatus = com.typenull.pingdom.post.domain.MapImageVisibilityStatus.ACTIVE
                      )
                    ORDER BY b.createdAt DESC, b.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(b)
                    FROM MapBookmark b
                    WHERE b.userId = :userId
                      AND EXISTS (
                          SELECT 1
                          FROM MapImage m
                          WHERE m.mapPlace.id = b.placeId
                            AND m.visibilityStatus = com.typenull.pingdom.post.domain.MapImageVisibilityStatus.ACTIVE
                      )
                    """
    )
    Page<MapImage> findBookmarkedByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "mapPlace")
    @Query(
            value = """
                    SELECT m
                    FROM MapImage m
                    JOIN MapImageLike liked ON liked.mapImageId = m.id
                    WHERE liked.userId = :userId
                      AND m.visibilityStatus = com.typenull.pingdom.post.domain.MapImageVisibilityStatus.ACTIVE
                    ORDER BY liked.likeId DESC
                    """,
            countQuery = """
                    SELECT COUNT(liked)
                    FROM MapImageLike liked
                    JOIN MapImage m ON m.id = liked.mapImageId
                    WHERE liked.userId = :userId
                      AND m.visibilityStatus = com.typenull.pingdom.post.domain.MapImageVisibilityStatus.ACTIVE
                    """
    )
    Page<MapImage> findLikedByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "mapPlace")
    @Query("""
            SELECT m
            FROM MapImage m
            LEFT JOIN m.mapPlace p
            WHERE (:reportStatus IS NULL OR EXISTS (
                       SELECT 1
                       FROM PostReport pr
                       WHERE pr.mapImage = m
                         AND pr.status = :reportStatus
                  ))
              AND (
                       :keyword = ''
                    OR m.title LIKE CONCAT('%', :keyword, '%')
                    OR m.username LIKE CONCAT('%', :keyword, '%')
                    OR m.description LIKE CONCAT('%', :keyword, '%')
                    OR p.name LIKE CONCAT('%', :keyword, '%')
                    OR (:numericKeyword IS NOT NULL AND (m.id = :numericKeyword OR m.userId = :numericKeyword))
                  )
            """)
    Page<MapImage> searchAdminPosts(
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            @Param("reportStatus") PostReportStatus reportStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "mapPlace")
    Optional<MapImage> findWithMapPlaceById(Long id);

    @EntityGraph(attributePaths = "mapPlace")
    Optional<MapImage> findWithMapPlaceByIdAndVisibilityStatus(Long id, MapImageVisibilityStatus visibilityStatus);

    long countByMapPlace_Id(Long placeId);

    long countByMapPlace_IdAndVisibilityStatus(Long placeId, MapImageVisibilityStatus visibilityStatus);

    List<MapImage> findByMapPlace_Id(Long placeId);

    List<MapImage> findByMapPlace_Id(Long placeId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(m.likeCount), 0)
            FROM MapImage m
            WHERE m.mapPlace.id = :placeId
            """)
    long sumLikeCountByPlaceId(@Param("placeId") Long placeId);

    @Query("""
            SELECT MAX(m.createdAt)
            FROM MapImage m
            WHERE m.mapPlace.id = :placeId
            """)
    LocalDateTime findLatestCreatedAtByPlaceId(@Param("placeId") Long placeId);

    @Query("SELECT COUNT(m) FROM MapImage m WHERE m.mapPlace.id = :placeId AND (:keyword IS NULL OR :keyword = '' OR m.title LIKE %:keyword%)")
    long countByMapPlace_IdAndTitleContaining(
            @Param("placeId") Long placeId,
            @Param("keyword") String keyword
    );

    @Query("SELECT m FROM MapImage m WHERE m.mapPlace.id = :placeId AND (:keyword IS NULL OR :keyword = '' OR m.title LIKE %:keyword%)")
    Page<MapImage> findByMapPlace_IdAndTitleContaining(
            @Param("placeId") Long placeId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT m.mapPlace.id
            FROM MapImage m
            WHERE m.userId = :userId
              AND m.mapPlace IS NOT NULL
            """)
    List<Long> findPlaceIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT m.mapPlace.id as placeId,
                   COALESCE(SUM(m.likeCount), 0) as likeSum,
                   MAX(m.createdAt) as latestCreatedAt
            FROM MapImage m
            WHERE m.mapPlace.id IN :placeIds
            GROUP BY m.mapPlace.id
            """)
    List<PlaceImageAggregateProjection> findPlaceAggregatesByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    boolean existsByUserIdAndMapPlace_Id(Long userId, Long placeId);

    @Query("""
            SELECT m.s3Key
            FROM MapImage m
            WHERE m.s3Key IN :keys
            """)
    List<String> findUsedOriginalS3Keys(@Param("keys") Collection<String> keys);

    @Query("""
            SELECT m.thumbnailS3Key
            FROM MapImage m
            WHERE m.thumbnailS3Key IN :keys
            """)
    List<String> findUsedThumbnailS3Keys(@Param("keys") Collection<String> keys);

    @Query("SELECT COUNT(m) FROM MapImage m WHERE m.s3Key IS NOT NULL AND m.s3Key <> ''")
    long countOriginalS3Keys();

    @Query("SELECT COUNT(m) FROM MapImage m WHERE m.thumbnailS3Key IS NOT NULL AND m.thumbnailS3Key <> ''")
    long countThumbnailS3Keys();

    @Query("""
            SELECT m.s3Key
            FROM MapImage m
            WHERE m.s3Key IS NOT NULL
            ORDER BY m.id ASC
            """)
    Slice<String> findS3Keys(Pageable pageable);

    @Query("""
            SELECT m.thumbnailS3Key
            FROM MapImage m
            WHERE m.thumbnailS3Key IS NOT NULL
            ORDER BY m.id ASC
            """)
    Slice<String> findThumbnailS3Keys(Pageable pageable);

    @Query("SELECT m FROM MapImage m WHERE m.id = :id OR m.userId = :id")
    Page<MapImage> findByIdOrUserId(@Param("id") Long id, Pageable pageable);

    @Query("SELECT m FROM MapImage m WHERE m.title LIKE %:keyword% OR m.description LIKE %:keyword%")
    Page<MapImage> findByTitleOrDescriptionContaining(@Param("keyword") String keyword, Pageable pageable);
}
