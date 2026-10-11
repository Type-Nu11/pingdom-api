package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostComment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, Long> {

    Optional<CommunityPostComment> findByIdAndCommunityPost_Id(Long commentId, Long postId);

    Optional<CommunityPostComment> findByIdAndCommunityPost_IdAndHiddenFalseAndCommunityPost_HiddenFalse(
            Long commentId,
            Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from CommunityPostComment comment where comment.id = :commentId")
    Optional<CommunityPostComment> findByIdForUpdate(@Param("commentId") Long commentId);

    @Query(value = """
            select comment from CommunityPostComment comment left join fetch comment.parentComment parent
            where comment.communityPost.id = :postId and comment.hidden = false
              and (parent is null or parent.hidden = false)
              and (:rootOnly = false or parent is null)
            """, countQuery = """
            select count(comment) from CommunityPostComment comment left join comment.parentComment parent
            where comment.communityPost.id = :postId and comment.hidden = false
              and (parent is null or parent.hidden = false)
              and (:rootOnly = false or parent is null)
            """)
    Page<CommunityPostComment> findVisibleComments(@Param("postId") long postId,
            @Param("rootOnly") boolean rootOnly, Pageable pageable);

    Page<CommunityPostComment> findByCommunityPost_IdAndParentComment_IdAndHiddenFalse(
            long postId, long parentId, Pageable pageable);

    @Query("""
            select comment from CommunityPostComment comment left join comment.parentComment parent
            where comment.id = :commentId and comment.communityPost.id = :postId
              and comment.hidden = false and comment.communityPost.hidden = false
              and (parent is null or parent.hidden = false)
            """)
    Optional<CommunityPostComment> findVisibleComment(@Param("postId") long postId, @Param("commentId") long commentId);

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
}
