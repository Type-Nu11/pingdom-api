package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostDetailResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentListResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CommunityPostQueryServiceTest {

    private final CommunityPostRepository communityPostRepository = mock(CommunityPostRepository.class);
    private final CommunityPostPlaceRepository communityPostPlaceRepository = mock(CommunityPostPlaceRepository.class);
    private final CommunityPostCommentRepository communityPostCommentRepository = mock(CommunityPostCommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommunityPostQueryService service = new CommunityPostQueryService(
            communityPostRepository,
            communityPostPlaceRepository,
            communityPostCommentRepository,
            userRepository
    );

    @Test
    void 카테고리에_속한_게시글의_ID와_제목만_페이지로_조회한다() {
        List<CommunityPostListResponse.Item> items = List.of(
                new CommunityPostListResponse.Item(2L, "두 번째 게시글"),
                new CommunityPostListResponse.Item(1L, "첫 번째 게시글")
        );
        when(communityPostRepository.findListItemsByCategoryId(any(), any()))
                .thenReturn(new PageImpl<>(items, PageRequest.of(0, 20), 22));

        CommunityPostListResponse response = service.findByCategory("TRAVEL", 1, 20);

        assertThat(response.posts()).containsExactlyElementsOf(items);
        assertThat(response.totalCount()).isEqualTo(22);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        verify(communityPostRepository).findListItemsByCategoryId(any(), any());
    }

    @Test
    void 지원하지_않는_카테고리는_조회하지_않는다() {
        assertThatThrownBy(() -> service.findByCategory("UNKNOWN", 1, 20))
                .isInstanceOf(CommunityException.class)
                .hasMessage("사용할 수 없는 게시글 카테고리입니다.");

        verify(communityPostRepository, never()).findListItemsByCategoryId(any(), any());
    }

    @Test
    void 게시글_본문과_연결_장소를_한번에_조회한다() {
        CommunityPost post = mock(CommunityPost.class);
        CommunityPostPlace postPlace = mock(CommunityPostPlace.class);
        MapPlace place = mock(MapPlace.class);
        when(post.getId()).thenReturn(10L);
        when(post.getTitle()).thenReturn("게시글 제목");
        when(post.getContent()).thenReturn("게시글 본문");
        when(postPlace.getMapPlace()).thenReturn(place);
        when(place.getId()).thenReturn(7L);
        when(place.getName()).thenReturn("대소고");
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(10L)).thenReturn(List.of(postPlace));

        CommunityPostDetailResponse response = service.findDetail(10L);

        assertThat(response.title()).isEqualTo("게시글 제목");
        assertThat(response.content()).isEqualTo("게시글 본문");
        assertThat(response.places()).containsExactly(new CommunityPostDetailResponse.Place(7L, "대소고", false));
        verify(communityPostPlaceRepository).findAllWithMapPlaceByCommunityPostId(10L);
    }

    @Test
    void 존재하지_않는_게시글은_장소를_조회하지_않는다() {
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(10L))
                .isInstanceOf(CommunityException.class)
                .hasMessage("게시글을 찾을 수 없습니다.");

        verify(communityPostPlaceRepository, never()).findAllWithMapPlaceByCommunityPostId(any());
    }

    @Test
    void 삭제된_연결_장소는_식별자와_삭제_안내를_유지한다() {
        CommunityPost post = mock(CommunityPost.class);
        CommunityPostPlace deletedPlace = mock(CommunityPostPlace.class);
        when(post.getId()).thenReturn(10L);
        when(post.getTitle()).thenReturn("게시글 제목");
        when(post.getContent()).thenReturn("게시글 본문");
        when(deletedPlace.getMapPlace()).thenReturn(null);
        when(deletedPlace.getMapPlaceId()).thenReturn(7L);
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(10L)).thenReturn(List.of(deletedPlace));

        CommunityPostDetailResponse response = service.findDetail(10L);

        assertThat(response.places())
                .containsExactly(new CommunityPostDetailResponse.Place(7L, "삭제된 장소입니다", true));
    }

    @Test
    void 게시글의_댓글만_최신순_페이지로_조회하고_작성자_정보를_일괄_결합한다() {
        CommunityPost post = mock(CommunityPost.class);
        CommunityPostComment comment = mock(CommunityPostComment.class);
        User author = mock(User.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 12, 12, 0);
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(comment.getId()).thenReturn(20L);
        when(comment.getContent()).thenReturn("댓글 내용");
        when(comment.getUserId()).thenReturn(7L);
        when(comment.getCreatedAt()).thenReturn(createdAt);
        when(author.getId()).thenReturn(7L);
        when(author.getUsername()).thenReturn("pingdom");
        when(communityPostCommentRepository.findByCommunityPost_IdAndHiddenFalse(any(), any()))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(author));

        CommunityPostCommentListResponse response = service.findComments(10L, 1, 20);

        assertThat(response.comments()).containsExactly(
                new CommunityPostCommentListResponse.Item(20L, "댓글 내용", 7L, "pingdom", createdAt)
        );
        verify(communityPostCommentRepository).findByCommunityPost_IdAndHiddenFalse(any(), any());
        verify(userRepository).findAllById(List.of(7L));
    }
}
