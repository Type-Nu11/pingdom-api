package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostMedia;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostMediaRepository extends JpaRepository<CommunityPostMedia, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from CommunityPostMedia media where media.id = :id")
    Optional<CommunityPostMedia> findByIdForUpdate(@Param("id") long id);

    // 연결 처리와 같은 행을 잠가 사용 중인 사진의 삭제를 막고, 다른 작업자가 잠근 행은 다음 실행으로 넘긴다.
    @Query(value = """
            SELECT * FROM community_post_media
            WHERE status = 'UPLOADED' AND expires_at <= :now
            ORDER BY community_post_media_id
            LIMIT 100 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CommunityPostMedia> findExpiredForUpdate(@Param("now") LocalDateTime now);

    List<CommunityPostMedia> findByCommunityPost_IdOrderByDisplayOrderAsc(long postId);
}
