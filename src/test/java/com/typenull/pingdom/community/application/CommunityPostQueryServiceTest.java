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
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CommunityPostQueryServiceTest {

    private final CommunityPostRepository communityPostRepository = mock(CommunityPostRepository.class);
    private final CommunityPostPlaceRepository communityPostPlaceRepository = mock(CommunityPostPlaceRepository.class);
    private final CommunityPostQueryService service = new CommunityPostQueryService(
            communityPostRepository,
            communityPostPlaceRepository
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
        when(communityPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(10L)).thenReturn(List.of(postPlace));

        CommunityPostDetailResponse response = service.findDetail(10L);

        assertThat(response.title()).isEqualTo("게시글 제목");
        assertThat(response.content()).isEqualTo("게시글 본문");
        assertThat(response.places()).containsExactly(new CommunityPostDetailResponse.Place(7L, "대소고", false));
        verify(communityPostPlaceRepository).findAllWithMapPlaceByCommunityPostId(10L);
    }

    @Test
    void 존재하지_않는_게시글은_장소를_조회하지_않는다() {
        when(communityPostRepository.findById(10L)).thenReturn(Optional.empty());

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
        when(communityPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(10L)).thenReturn(List.of(deletedPlace));

        CommunityPostDetailResponse response = service.findDetail(10L);

        assertThat(response.places())
                .containsExactly(new CommunityPostDetailResponse.Place(7L, "삭제된 장소입니다", true));
    }
}
