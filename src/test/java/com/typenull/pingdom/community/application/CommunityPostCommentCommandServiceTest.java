package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommunityPostCommentCommandServiceTest {

    private final CommunityPostRepository communityPostRepository = mock(CommunityPostRepository.class);
    private final CommunityPostCommentRepository communityPostCommentRepository = mock(CommunityPostCommentRepository.class);
    private final CommunityPostCommentCommandService service = new CommunityPostCommentCommandService(
            communityPostRepository,
            communityPostCommentRepository
    );

    @Test
    void 인증된_사용자를_작성자로_댓글을_저장한다() {
        CommunityPost post = mock(CommunityPost.class);
        CommunityPostComment savedComment = mock(CommunityPostComment.class);
        when(post.getId()).thenReturn(10L);
        when(savedComment.getId()).thenReturn(20L);
        when(savedComment.getContent()).thenReturn("댓글 내용");
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(communityPostCommentRepository.save(any(CommunityPostComment.class))).thenReturn(savedComment);

        CommunityPostCommentCreateResponse response = service.create(
                10L,
                7L,
                new CommunityPostCommentCreateRequest("댓글 내용")
        );

        assertThat(response.commentId()).isEqualTo(20L);
        assertThat(response.postId()).isEqualTo(10L);
        verify(communityPostCommentRepository).save(any(CommunityPostComment.class));
    }

    @Test
    void 존재하지_않는_게시글에는_댓글을_저장하지_않는다() {
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(10L, 7L, new CommunityPostCommentCreateRequest("댓글 내용")))
                .isInstanceOf(CommunityException.class)
                .hasMessage("게시글을 찾을 수 없습니다.");

        verify(communityPostCommentRepository, never()).save(any());
    }
}
