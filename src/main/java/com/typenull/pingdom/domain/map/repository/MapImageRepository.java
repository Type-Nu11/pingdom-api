package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapImage;
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
}
