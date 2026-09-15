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

    Optional<CommunityPostComment> findByIdAndCommunityPost_IdAndHiddenFalseAndCommunityPost_HiddenFalse(
            Long commentId,
            Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from CommunityPostComment comment where comment.id = :commentId")
    Optional<CommunityPostComment> findByIdForUpdate(@Param("commentId") Long commentId);

    Page<CommunityPostComment> findByCommunityPost_IdAndHiddenFalse(Long postId, Pageable pageable);
}
