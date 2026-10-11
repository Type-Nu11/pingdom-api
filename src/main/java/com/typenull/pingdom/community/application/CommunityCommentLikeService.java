package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityCommentLikeResponse;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityCommentLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 댓글만 반응 대상으로 허용하고 반복 등록·취소를 멱등적으로 처리. */
@Service
@RequiredArgsConstructor
public class CommunityCommentLikeService {
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityCommentLikeRepository likeRepository;

    @Transactional
    public CommunityCommentLikeResponse like(long postId, long commentId, long userId) {
        requireVisibleForUpdate(postId, commentId);
        likeRepository.insertIgnoreDuplicate(commentId, userId);
        return response(commentId, userId);
    }

    @Transactional
    public CommunityCommentLikeResponse cancel(long postId, long commentId, long userId) {
        requireVisibleForUpdate(postId, commentId);
        likeRepository.deleteByCommunityPostCommentIdAndUserId(commentId, userId);
        return response(commentId, userId);
    }

    @Transactional(readOnly = true)
    public CommunityCommentLikeResponse find(long postId, long commentId, long userId) {
        commentRepository.findVisibleComment(postId, commentId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        return response(commentId, userId);
    }

    private void requireVisibleForUpdate(long postId, long commentId) {
        CommunityPostComment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getCommunityPost().getId().equals(postId) || comment.isHidden()
                || comment.getCommunityPost().isHidden()
                || (comment.getParentComment() != null && comment.getParentComment().isHidden())) {
            throw new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private CommunityCommentLikeResponse response(long commentId, long userId) {
        return new CommunityCommentLikeResponse(commentId, likeRepository.countByCommunityPostCommentId(commentId),
                likeRepository.existsByCommunityPostCommentIdAndUserId(commentId, userId));
    }
}
