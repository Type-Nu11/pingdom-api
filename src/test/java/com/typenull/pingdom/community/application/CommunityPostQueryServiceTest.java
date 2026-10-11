package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostMediaRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityCommentLikeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
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
            userRepository, mock(CommunityPostMediaRepository.class), mock(CommunityCommentLikeRepository.class),
            mock(PlaceAdministrativeRegionRepository.class)
    );

    /**
     * 카테고리 조회가 저장소의 ID·제목 목록과 전체 수·페이지 수·다음 페이지 여부를 응답에 반영하는지 검증.
     */
    @Test
    void returnsCategoryPostPage() {
        CommunityPostRepository.ListItem first = mock(CommunityPostRepository.ListItem.class);
        CommunityPostRepository.ListItem second = mock(CommunityPostRepository.ListItem.class);
        when(first.getPostId()).thenReturn(2L);
        when(first.getTitle()).thenReturn("두 번째 게시글");
        when(first.getContent()).thenReturn("본문\n  미리보기");
        when(first.getCountry()).thenReturn("한국");
        when(first.getCategoryId()).thenReturn("TRAVEL");
        when(second.getPostId()).thenReturn(1L);
        when(communityPostRepository.findListItems(any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 22));
        CommunityPostListResponse response = service.findByCategory("TRAVEL", 1, 20, null);
        assertThat(response.posts()).extracting(CommunityPostListResponse.Item::postId).containsExactly(2L, 1L);
        assertThat(response.posts().getFirst().preview()).isEqualTo("본문 미리보기");
        assertThat(response.posts().getFirst().country()).isEqualTo("KR");
        assertThat(response.totalCount()).isEqualTo(22);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();

    }

    /**
     * 알 수 없는 카테고리 조회는 해당 오류 메시지를 반환하고 게시글 목록 저장소를 호출하지 않는지 검증.
     */
    @Test
    void rejectsUnsupportedQueryCategory() {
        assertThatThrownBy(() -> service.findByCategory("UNKNOWN", 1, 20, null))
                .isInstanceOf(CommunityException.class)
                .hasMessage("사용할 수 없는 게시글 카테고리입니다.");

        verify(communityPostRepository, never()).findListItems(any(), anyBoolean(), any(), any());
    }

    /**
     * 게시글 상세의 제목·본문과 연결 장소 ID·이름·삭제 여부를 조립하고 연결 장소 일괄 조회를 사용하는지 검증.
     */
    @Test
    void returnsPostWithLinkedPlaces() {
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
        assertThat(response.places()).containsExactly(new CommunityPostDetailResponse.Place(7L, "대소고", false, null, null, null));
        verify(communityPostPlaceRepository).findAllWithMapPlaceByCommunityPostId(10L);
    }

    /**
     * 조회 가능한 게시글이 없으면 게시글 없음 오류를 반환하고 연결 장소 조회를 생략하는지 검증.
     */
    @Test
    void rejectsMissingPostDetail() {
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(10L))
                .isInstanceOf(CommunityException.class)
                .hasMessage("게시글을 찾을 수 없습니다.");

        verify(communityPostPlaceRepository, never()).findAllWithMapPlaceByCommunityPostId(any());
    }

    /**
     * 연결된 장소 객체가 삭제되어도 원래 장소 ID와 삭제 안내 문구, 삭제 상태를 상세 응답에 유지하는지 검증.
     */
    @Test
    void preservesDeletedPlaceReference() {
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
                .containsExactly(new CommunityPostDetailResponse.Place(7L, "삭제된 장소입니다", true, null, null, null));
    }

    /**
     * 댓글 페이지와 작성자 일괄 조회 결과를 결합해 댓글 ID·내용·작성자 ID·이름·작성 시각을 응답하는지 검증.
     */
    @Test
    void joinsCommentAuthorInformation() {
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
        when(communityPostCommentRepository.findVisibleComments(anyLong(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(author));

        CommunityPostCommentListResponse response = service.findComments(10L, 1, 20, 7L, false);

        assertThat(response.comments()).containsExactly(
                new CommunityPostCommentListResponse.Item(20L, "댓글 내용", 7L, "pingdom", createdAt, null, 0, false)
        );
        verify(communityPostCommentRepository).findVisibleComments(anyLong(), anyBoolean(), any());
        verify(userRepository).findAllById(List.of(7L));
    }
    @Test
    void exposesPostAuthorAndCategory() {
        CommunityPost post = mock(CommunityPost.class);
        User author = mock(User.class);
        when(post.getId()).thenReturn(10L);
        when(post.getUserId()).thenReturn(7L);
        when(post.getCategoryId()).thenReturn("TRAVEL");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 12, 34, 56);
        when(post.getCreatedAt()).thenReturn(createdAt);
        when(author.getUsername()).thenReturn("여행자");
        when(author.getProfileImageUrl()).thenReturn("https://example.com/profile.png");
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));

        CommunityPostDetailResponse response = service.findDetail(10L);

        assertThat(response.author()).isEqualTo(new CommunityPostDetailResponse.Author(
                7L, "여행자", "https://example.com/profile.png"));
        assertThat(response.category()).isEqualTo(new CommunityPostDetailResponse.Category("TRAVEL", "여행"));
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void doesNotExposeWithdrawnAuthorProfile() {
        CommunityPost post = mock(CommunityPost.class);
        User author = mock(User.class);
        when(post.getId()).thenReturn(10L);
        when(post.getUserId()).thenReturn(7L);
        when(author.isWithdrawn()).thenReturn(true);
        when(author.getUsername()).thenReturn("이전 작성자 이름");
        when(author.getProfileImageUrl()).thenReturn("https://example.com/old-profile.png");
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));

        assertThat(service.findDetail(10L).author()).isEqualTo(
                new CommunityPostDetailResponse.Author(7L, User.WITHDRAWN_DISPLAY_NAME, null));
    }

    @Test
    void preservesAuthorIdWhenAuthorRowIsMissing() {
        CommunityPost post = mock(CommunityPost.class);
        when(post.getId()).thenReturn(10L);
        when(post.getUserId()).thenReturn(7L);
        when(communityPostRepository.findByIdAndHiddenFalse(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(service.findDetail(10L).author()).isEqualTo(
                new CommunityPostDetailResponse.Author(7L, "알 수 없는 사용자", null));
    }

}
