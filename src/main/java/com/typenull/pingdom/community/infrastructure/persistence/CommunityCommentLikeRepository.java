package com.typenull.pingdom.community.infrastructure.persistence;
import com.typenull.pingdom.community.domain.CommunityCommentLike;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLike, Long> {
    @Modifying @Query(value = "insert into community_comment_like(comment_id,user_id) values(:commentId,:userId) on conflict(comment_id,user_id) do nothing", nativeQuery = true)
    int insertIgnoreDuplicate(@Param("commentId") long commentId, @Param("userId") long userId);
    long deleteByCommentIdAndUserId(long commentId, long userId);
    long countByCommentId(long commentId);
    boolean existsByCommentIdAndUserId(long commentId, long userId);
}
