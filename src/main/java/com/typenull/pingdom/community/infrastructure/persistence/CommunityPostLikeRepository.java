package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게시글·사용자 유일 조합의 충돌 무시 삽입으로 중복 좋아요 추가를 허용.
 * 좋아요 총수는 별도 카운터 대신 이력 행 수로 계산하며 게시글 공개 여부 검증은 서비스가 담당.
 */
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
