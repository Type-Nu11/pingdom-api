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

    @Test
    void 중복_좋아요도_하나의_좋아요_상태로_반환한다() {
        when(postRepository.existsById(10L)).thenReturn(true);
        when(likeRepository.countByCommunityPostId(10L)).thenReturn(3L);
        when(likeRepository.existsByCommunityPostIdAndUserId(10L, 7L)).thenReturn(true);

        CommunityPostLikeResponse response = service.like(10L, 7L);

        assertThat(response).isEqualTo(new CommunityPostLikeResponse(10L, 3L, true));
        verify(likeRepository).insertIgnoreDuplicate(10L, 7L);
    }

    @Test
    void 이미_취소한_좋아요도_취소된_상태를_반환한다() {
        when(postRepository.existsById(10L)).thenReturn(true);
        when(likeRepository.countByCommunityPostId(10L)).thenReturn(2L);
        when(likeRepository.existsByCommunityPostIdAndUserId(10L, 7L)).thenReturn(false);

        assertThat(service.cancel(10L, 7L)).isEqualTo(new CommunityPostLikeResponse(10L, 2L, false));
    }

    @Test
    void 없는_게시글에는_좋아요를_등록하지_않는다() {
        when(postRepository.existsById(10L)).thenReturn(false);
        assertThatThrownBy(() -> service.like(10L, 7L)).isInstanceOf(CommunityException.class);
        verifyNoInteractions(likeRepository);
    }
}
