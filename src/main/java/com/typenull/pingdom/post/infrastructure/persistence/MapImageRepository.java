package com.typenull.pingdom.post.infrastructure.persistence;

import com.typenull.pingdom.post.domain.MapImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MapImageRepository extends JpaRepository<MapImage,Long> {

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

    @EntityGraph(attributePaths = "mapPlace")
    Page<MapImage> findAllBy(Pageable pageable);

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
}
