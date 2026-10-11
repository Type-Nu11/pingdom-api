package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityCommentLikeResponse;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityCommentLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 댓글의 좋아요 추가·취소를 반복 호출 가능한 형태로 처리.
 * 추가는 DB 충돌 무시 삽입을 사용하고 취소는 없는 이력도 허용하며, 응답 집계와 내 상태는 각각 다시 조회.
 */
@Service
@RequiredArgsConstructor
public class CommunityCommentLikeService {

    private final CommunityPostCommentRepository comments;
    private final CommunityCommentLikeRepository likes;

    @Transactional
    public CommunityCommentLikeResponse like(long commentId, long userId) {
        requireComment(commentId);
        likes.insertIgnoreDuplicate(commentId, userId);
        return response(commentId, userId);
    }

    @Transactional
    public CommunityCommentLikeResponse cancel(long commentId, long userId) {
        requireComment(commentId);
        likes.deleteByCommentIdAndUserId(commentId, userId);
        return response(commentId, userId);
    }

    @Transactional(readOnly = true)
    public CommunityCommentLikeResponse find(long commentId, long userId) {
        requireComment(commentId);
        return response(commentId, userId);
    }

    private void requireComment(long commentId) {
        var comment = comments.findById(commentId)
                .filter(candidate -> !candidate.isHidden() && !candidate.getCommunityPost().isHidden())
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        // 부모가 숨겨진 답글도 공개 목록과 동일하게 좋아요 대상으로 노출하지 않습니다.
        if (comment.getParentCommentId() != null && comments.findById(comment.getParentCommentId())
                .filter(parent -> !parent.isHidden()).isEmpty()) {
            throw new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private CommunityCommentLikeResponse response(long commentId, long userId) {
        return new CommunityCommentLikeResponse(
                commentId,
                likes.countByCommentId(commentId),
                likes.existsByCommentIdAndUserId(commentId, userId)
        );
    }
}
