package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.community.api.dto.CommunityPostLikeResponse;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import org.junit.jupiter.api.Test;

class CommunityPostLikeServiceTest {
    private final CommunityPostRepository postRepository = mock(CommunityPostRepository.class);
    private final CommunityPostLikeRepository likeRepository = mock(CommunityPostLikeRepository.class);
    private final CommunityPostLikeService service = new CommunityPostLikeService(postRepository, likeRepository);

    /**
     * 좋아요 요청이 중복 무시 삽입을 호출하고 현재 집계 수와 좋아요 상태를 응답하는지 검증한다.
     */
    @Test
    void returnsExistingLikeState() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(likeRepository.countByCommunityPostId(10L)).thenReturn(3L);
        when(likeRepository.existsByCommunityPostIdAndUserId(10L, 7L)).thenReturn(true);

        CommunityPostLikeResponse response = service.like(10L, 7L);

        assertThat(response).isEqualTo(new CommunityPostLikeResponse(10L, 3L, true));
        verify(likeRepository).insertIgnoreDuplicate(10L, 7L);
    }

    /**
     * 이미 좋아요가 없는 상태에서 취소해도 현재 집계 수와 false 상태를 정상 응답하는지 검증한다.
     */
    @Test
    void returnsCancelledLikeState() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(true);
        when(likeRepository.countByCommunityPostId(10L)).thenReturn(2L);
        when(likeRepository.existsByCommunityPostIdAndUserId(10L, 7L)).thenReturn(false);

        assertThat(service.cancel(10L, 7L)).isEqualTo(new CommunityPostLikeResponse(10L, 2L, false));
    }

    /**
     * 공개 게시글이 없으면 예외를 반환하고 좋아요 저장소를 호출하지 않는지 검증한다.
     */
    @Test
    void rejectsLikeForMissingPost() {
        when(postRepository.existsByIdAndHiddenFalse(10L)).thenReturn(false);
        assertThatThrownBy(() -> service.like(10L, 7L)).isInstanceOf(CommunityException.class);
        verifyNoInteractions(likeRepository);
    }
}
