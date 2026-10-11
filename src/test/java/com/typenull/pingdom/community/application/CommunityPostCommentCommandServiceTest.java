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

    /**
     * 조회 가능한 게시글에 댓글을 생성하면 저장소를 호출하고 저장된 댓글 ID와 게시글 ID를 응답하는지 검증.
     */
    @Test
    void savesCommentForVisiblePost() {
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

    /**
     * 게시글을 조회할 수 없으면 게시글 없음 오류를 반환하고 댓글을 저장하지 않는지 검증.
     */
    @Test
    void rejectsCommentForMissingPost() {
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(10L, 7L, new CommunityPostCommentCreateRequest("댓글 내용")))
                .isInstanceOf(CommunityException.class)
                .hasMessage("게시글을 찾을 수 없습니다.");

        verify(communityPostCommentRepository, never()).save(any());
    }
}
