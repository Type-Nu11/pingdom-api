package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityCommentLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 댓글·사용자 유일 조합의 충돌 무시 삽입으로 중복 좋아요 추가를 허용.
 * 좋아요 총수는 별도 카운터 대신 이력 행 수로 계산하며 댓글 공개 여부 검증은 서비스가 담당.
 */
public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLike, Long> {

    interface LikeCount {
        Long getCommentId();
        long getLikeCount();
    }

    @Query("select l.communityPostCommentId as commentId, count(l) as likeCount from CommunityCommentLike l "
            + "where l.communityPostCommentId in :ids group by l.communityPostCommentId")
    List<LikeCount> countForComments(@Param("ids") List<Long> ids);

    @Query("select l.communityPostCommentId from CommunityCommentLike l "
            + "where l.communityPostCommentId in :ids and l.userId = :userId")
    List<Long> findLikedCommentIds(@Param("ids") List<Long> ids, @Param("userId") long userId);

    @Modifying
    @Query(value = """
            INSERT INTO community_comment_like (community_post_comment_id, user_id)
            VALUES (:commentId, :userId)
            ON CONFLICT (community_post_comment_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(@Param("commentId") long commentId, @Param("userId") long userId);

    long deleteByCommunityPostCommentIdAndUserId(long commentId, long userId);

    long countByCommunityPostCommentId(long commentId);

    boolean existsByCommunityPostCommentIdAndUserId(long commentId, long userId);
}
