package com.typenull.pingdom.community.infrastructure.persistence;
import com.typenull.pingdom.community.domain.CommunityPostImage;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface CommunityPostImageRepository extends JpaRepository<CommunityPostImage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from CommunityPostImage image where image.id in :ids order by image.id")
    List<CommunityPostImage> findAllByIdForUpdate(@Param("ids") List<Long> ids);
    List<CommunityPostImage> findByPostIdOrderByPositionAsc(Long postId);
    @Query(value = "select * from community_post_image where post_id is null and expires_at < :now order by id limit 100 for update skip locked", nativeQuery = true)
    List<CommunityPostImage> findExpiredForUpdate(@Param("now") LocalDateTime now);
}
