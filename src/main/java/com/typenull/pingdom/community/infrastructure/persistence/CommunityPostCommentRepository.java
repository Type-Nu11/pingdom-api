package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostComment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, Long> {

    Optional<CommunityPostComment> findByIdAndCommunityPost_Id(Long commentId, Long postId);

    Optional<CommunityPostComment> findByIdAndCommunityPost_IdAndHiddenFalseAndCommunityPost_HiddenFalse(
            Long commentId,
            Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from CommunityPostComment comment where comment.id = :commentId")
    Optional<CommunityPostComment> findByIdForUpdate(@Param("commentId") Long commentId);

    @Query("select c from CommunityPostComment c where c.communityPost.id = :postId and c.hidden = false and (c.parentCommentId is null or exists (select p.id from CommunityPostComment p where p.id = c.parentCommentId and p.hidden = false))")
    Page<CommunityPostComment> findByCommunityPost_IdAndHiddenFalse(@Param("postId") Long postId, Pageable pageable);

    @Query("""
            select comment from CommunityPostComment comment
            where comment.communityPost.id = :postId
              and (:hidden is null or comment.hidden = :hidden)
            """)
    Page<CommunityPostComment> findAllForAdmin(
            @Param("postId") Long postId,
            @Param("hidden") Boolean hidden,
            Pageable pageable
    );

    @Query("select c from CommunityPostComment c where c.communityPost.id = :postId and c.hidden = false and c.parentCommentId = :parentId")
    Page<CommunityPostComment> findReplies(@Param("postId") long postId, @Param("parentId") long parentId, Pageable pageable);

    @Query(value = """
        select c.community_post_comment_id as "commentId",
            (select count(*) from community_comment_like l where l.comment_id=c.community_post_comment_id) as "likeCount",
            exists(select 1 from community_comment_like l where l.comment_id=c.community_post_comment_id and l.user_id=:userId) as liked
        from community_post_comment c where c.community_post_comment_id in :ids
        """, nativeQuery = true)
    java.util.List<Reaction> findReactions(@Param("ids") java.util.List<Long> ids, @Param("userId") Long userId);
    interface Reaction { Long getCommentId(); long getLikeCount(); boolean getLiked(); }
}
