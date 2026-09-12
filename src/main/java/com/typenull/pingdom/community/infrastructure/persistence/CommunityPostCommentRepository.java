package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, Long> {
}
