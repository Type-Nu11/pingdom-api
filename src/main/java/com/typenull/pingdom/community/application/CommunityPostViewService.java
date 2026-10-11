package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostViewResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostDailyViewRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상세 GET은 조회 전용으로 유지하고 별도 요청으로 일별 최초 조회만 집계. */
@Service
@RequiredArgsConstructor
public class CommunityPostViewService {
    private final CommunityPostRepository postRepository;
    private final CommunityPostDailyViewRepository viewRepository;
    private final Clock clock;

    @Transactional
    public CommunityPostViewResponse record(long postId, long userId) {
        postRepository.findByIdForUpdate(postId).filter(post -> !post.isHidden())
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        if (viewRepository.insertIgnoreDuplicate(userId, postId, LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))) == 1) {
            postRepository.increaseViewCount(postId);
        }
        CommunityPost post = postRepository.findByIdAndHiddenFalse(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        return new CommunityPostViewResponse(postId, post.getViewCount());
    }
}
