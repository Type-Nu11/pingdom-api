package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostComment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, Long> {

    Optional<CommunityPostComment> findByIdAndCommunityPost_Id(Long commentId, Long postId);

    Page<CommunityPostComment> findByCommunityPost_Id(Long postId, Pageable pageable);
}
