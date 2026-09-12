package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostCommentCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostCommentRepository communityPostCommentRepository;

    @Transactional
    public CommunityPostCommentCreateResponse create(long postId, long userId, CommunityPostCommentCreateRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        CommunityPostComment comment = communityPostCommentRepository.save(CommunityPostComment.create(
                post,
                userId,
                request.content().trim()
        ));
        return new CommunityPostCommentCreateResponse(comment.getId(), post.getId(), comment.getContent());
    }
}
