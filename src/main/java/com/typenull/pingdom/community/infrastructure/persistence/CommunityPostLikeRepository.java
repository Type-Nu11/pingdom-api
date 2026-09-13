package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO community_post_like (community_post_id, user_id)
            VALUES (:postId, :userId)
            ON CONFLICT (community_post_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(@Param("postId") long postId, @Param("userId") long userId);

    long deleteByCommunityPostIdAndUserId(long postId, long userId);

    long countByCommunityPostId(long postId);

    boolean existsByCommunityPostIdAndUserId(long postId, long userId);
}
