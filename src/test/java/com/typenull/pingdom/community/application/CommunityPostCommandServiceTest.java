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

    /**
     * 두 장소를 연결해 게시글을 생성하면 게시글·연결 목록 저장을 호출하고 생성 ID와 요청 장소 순서를 응답하는지 검증한다.
     */
    @Test
    void savesPostWithLinkedPlaces() {
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

    /**
     * PLACE 카테고리에 연결 장소가 없으면 필수 장소 오류 메시지를 반환하고 게시글을 저장하지 않는지 검증한다.
     */
    @Test
    void rejectsPlacePostWithoutLinks() {
        assertThatThrownBy(() -> service.create(7L, request("PLACE", List.of())))
                .isInstanceOf(CommunityException.class)
                .hasMessage("장소 카테고리 게시글은 연결 장소를 1개 이상 선택해야 합니다.");

        verify(communityPostRepository, never()).save(any());
    }

    /**
     * 알 수 없는 카테고리는 오류로 거절하고 장소 조회와 게시글 저장을 진행하지 않는지 검증한다.
     */
    @Test
    void rejectsUnsupportedPostCategory() {
        assertThatThrownBy(() -> service.create(7L, request("UNKNOWN", List.of())))
                .isInstanceOf(CommunityException.class)
                .hasMessage("사용할 수 없는 게시글 카테고리입니다.");

        verify(mapPlaceRepository, never()).findAllById(any());
        verify(communityPostRepository, never()).save(any());
    }

    /**
     * 동일한 장소 ID가 반복된 생성 요청은 중복 연결 오류로 거절하고 장소 조회·게시글 저장을 생략하는지 검증한다.
     */
    @Test
    void rejectsDuplicatePlaceLinks() {
        assertThatThrownBy(() -> service.create(7L, request("TRAVEL", List.of(1L, 1L))))
                .isInstanceOf(CommunityException.class)
                .hasMessage("같은 장소를 중복해서 연결할 수 없습니다.");

        verify(mapPlaceRepository, never()).findAllById(any());
        verify(communityPostRepository, never()).save(any());
    }

    /**
     * 요청한 장소 중 일부가 조회되지 않으면 장소 없음 오류를 반환하고 게시글을 저장하지 않는지 검증한다.
     */
    @Test
    void rejectsMissingLinkedPlace() {
        when(mapPlaceRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(mock(MapPlace.class)));

        assertThatThrownBy(() -> service.create(7L, request("TRAVEL", List.of(1L, 2L))))
                .isInstanceOf(CommunityException.class)
                .hasMessage("연결할 장소를 찾을 수 없습니다.");

        verify(communityPostRepository, never()).save(any());
    }

    /**
     * 카테고리와 장소 목록만 바꾸어 생성 규칙을 검사하도록 고정 제목·본문의 요청을 만든다.
     */
    private CommunityPostCreateRequest request(String categoryId, List<Long> placeIds) {
        return new CommunityPostCreateRequest(categoryId, "게시글 제목", "게시글 본문", placeIds);
    }
}
