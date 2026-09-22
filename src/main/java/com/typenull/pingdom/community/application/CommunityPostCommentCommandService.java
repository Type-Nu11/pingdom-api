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

/**
 * 숨김 처리되지 않은 게시글에 댓글을 저장합니다.
 * 작성자 식별자는 인증 경계에서 전달받고, 본문의 공백 제거 외 길이·필수 값 검증은 요청 DTO가 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostCommentCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostCommentRepository communityPostCommentRepository;

    /**
     * 숨김되지 않은 게시글에 인증 경계에서 받은 작성자 ID로 댓글을 저장하고 댓글·게시글 ID와 정리된 본문을 반환합니다.
     * 숨김 또는 없는 게시글은 POST_NOT_FOUND로 거절합니다.
     */
    @Transactional
    public CommunityPostCommentCreateResponse create(long postId, long userId, CommunityPostCommentCreateRequest request) {
        CommunityPost post = communityPostRepository.findByIdAndHiddenFalse(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        CommunityPostComment comment = communityPostCommentRepository.save(CommunityPostComment.create(
                post,
                userId,
                request.content().trim()
        ));
        return new CommunityPostCommentCreateResponse(comment.getId(), post.getId(), comment.getContent());
    }
}
