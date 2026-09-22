package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostLikeResponse;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 게시글의 좋아요 추가·취소를 반복 호출 가능한 형태로 처리.
 * 추가는 DB 충돌 무시 삽입을 사용하고 취소는 없는 이력도 허용하며, 응답 집계와 내 상태는 각각 다시 조회.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostLikeService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;

    @Transactional
    public CommunityPostLikeResponse like(long postId, long userId) {
        requirePost(postId);
        communityPostLikeRepository.insertIgnoreDuplicate(postId, userId);
        return response(postId, userId);
    }

    @Transactional
    public CommunityPostLikeResponse cancel(long postId, long userId) {
        requirePost(postId);
        communityPostLikeRepository.deleteByCommunityPostIdAndUserId(postId, userId);
        return response(postId, userId);
    }

    @Transactional(readOnly = true)
    public CommunityPostLikeResponse find(long postId, long userId) {
        requirePost(postId);
        return response(postId, userId);
    }

    private void requirePost(long postId) {
        if (!communityPostRepository.existsByIdAndHiddenFalse(postId)) {
            throw new CommunityException(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    private CommunityPostLikeResponse response(long postId, long userId) {
        return new CommunityPostLikeResponse(
                postId,
                communityPostLikeRepository.countByCommunityPostId(postId),
                communityPostLikeRepository.existsByCommunityPostIdAndUserId(postId, userId)
        );
    }
}
