package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityPostCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCreateResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunityPostCommandServiceTest {

    private final CommunityPostRepository communityPostRepository = mock(CommunityPostRepository.class);
    private final CommunityPostPlaceRepository communityPostPlaceRepository = mock(CommunityPostPlaceRepository.class);
    private final MapPlaceRepository mapPlaceRepository = mock(MapPlaceRepository.class);
    private final CommunityPostCommandService service = new CommunityPostCommandService(
            communityPostRepository,
            communityPostPlaceRepository,
            mapPlaceRepository
    );

    @Test
    void 인증된_작성자의_게시글과_연결_장소를_함께_저장한다() {
        CommunityPost savedPost = mock(CommunityPost.class);
        MapPlace firstPlace = mock(MapPlace.class);
        MapPlace secondPlace = mock(MapPlace.class);
        when(savedPost.getId()).thenReturn(10L);
        when(communityPostRepository.save(any(CommunityPost.class))).thenReturn(savedPost);
        when(mapPlaceRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(firstPlace, secondPlace));

        CommunityPostCreateResponse response = service.create(7L, request("TRAVEL", List.of(1L, 2L)));

        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.placeIds()).containsExactly(1L, 2L);
        verify(communityPostRepository).save(any(CommunityPost.class));
        verify(communityPostPlaceRepository).saveAll(any());
    }

    @Test
    void 장소_카테고리는_연결_장소가_없으면_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(7L, request("PLACE", List.of())))
                .isInstanceOf(CommunityException.class)
                .hasMessage("장소 카테고리 게시글은 연결 장소를 1개 이상 선택해야 합니다.");

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void 사용할_수_없는_카테고리면_게시글을_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(7L, request("UNKNOWN", List.of())))
                .isInstanceOf(CommunityException.class)
                .hasMessage("사용할 수 없는 게시글 카테고리입니다.");

        verify(mapPlaceRepository, never()).findAllById(any());
        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void 동일한_장소를_중복_연결하면_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(7L, request("TRAVEL", List.of(1L, 1L))))
                .isInstanceOf(CommunityException.class)
                .hasMessage("같은 장소를 중복해서 연결할 수 없습니다.");

        verify(mapPlaceRepository, never()).findAllById(any());
        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_장소가_포함되면_게시글을_저장하지_않는다() {
        when(mapPlaceRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(mock(MapPlace.class)));

        assertThatThrownBy(() -> service.create(7L, request("TRAVEL", List.of(1L, 2L))))
                .isInstanceOf(CommunityException.class)
                .hasMessage("연결할 장소를 찾을 수 없습니다.");

        verify(communityPostRepository, never()).save(any());
    }

    private CommunityPostCreateRequest request(String categoryId, List<Long> placeIds) {
        return new CommunityPostCreateRequest(categoryId, "게시글 제목", "게시글 본문", placeIds);
    }
}
