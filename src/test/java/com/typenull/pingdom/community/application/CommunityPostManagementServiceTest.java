package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateRequest;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommunityPostManagementServiceTest {
    private final CommunityPostRepository posts = mock(CommunityPostRepository.class);
    private final CommunityPostPlaceRepository links = mock(CommunityPostPlaceRepository.class);
    private final MapPlaceRepository places = mock(MapPlaceRepository.class);
    private final CommunityPostImageService images = mock(CommunityPostImageService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC);
    private final CommunityPostCommandService service = new CommunityPostCommandService(posts, links, places, images, clock);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void partialUpdatePreservesOmittedFieldsAndClearsExplicitLocationNull() throws Exception {
        CommunityPost post = owned("TRAVEL");
        post.describeLocation("KR", "서울");
        var result = service.update(7L, 10L, request("{\"title\":\" 수정 제목 \",\"region\":null}"));
        assertThat(post.getTitle()).isEqualTo("수정 제목");
        assertThat(post.getContent()).isEqualTo("원본 본문");
        assertThat(post.getCountryCode()).isEqualTo("KR");
        assertThat(post.getRegion()).isNull();
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.now(clock));
        verifyNoInteractions(images);
        verify(links, never()).deleteAllByPostId(anyLong());
    }

    @Test void rejectsOtherUserHiddenDeletedAndMissingPostsBeforeAnyChange() throws Exception {
        CommunityPost post = owned("TRAVEL");
        var request = request("{\"title\":\"수정\"}");
        assertThatThrownBy(() -> service.update(8L, 10L, request)).isInstanceOf(CommunityException.class);
        post.hide(99L, LocalDateTime.now(clock));
        assertThatThrownBy(() -> service.update(7L, 10L, request)).isInstanceOf(CommunityException.class);
        post.delete(LocalDateTime.now(clock));
        assertThatThrownBy(() -> service.delete(7L, 10L)).isInstanceOf(CommunityException.class);
        when(posts.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, 10L, request)).isInstanceOf(CommunityException.class);
        assertThat(post.getTitle()).isEqualTo("원본 제목");
        verifyNoInteractions(images, links, places);
    }

    @Test void validatesFinalCategoryAndRetainedPlaceSelection() throws Exception {
        owned("TRAVEL");
        var request = request("{\"categoryId\":\"PLACE\"}");
        assertThatThrownBy(() -> service.update(7L, 10L, request)).isInstanceOf(CommunityException.class)
                .hasMessageContaining("연결 장소");
        CommunityPostPlace retained = mock(CommunityPostPlace.class);
        when(retained.getMapPlaceId()).thenReturn(11L);
        when(links.findAllWithMapPlaceByCommunityPostId(10L)).thenReturn(List.of(retained));
        service.update(7L, 10L, request);
        verify(places, never()).findAllById(any());
        verify(links, never()).deleteAllByPostId(anyLong());
    }

    @Test void rejectedImagesLeavePostAndPlaceRelationshipsUnchanged() throws Exception {
        CommunityPost post = owned("TRAVEL");
        doThrow(new CommunityException(com.typenull.pingdom.community.domain.exception.CommunityErrorCode.INVALID_IMAGE))
                .when(images).replace(7L, 10L, List.of(20L));
        var request = request("{\"title\":\"수정\",\"placeIds\":[],\"imageIds\":[20]}");
        assertThatThrownBy(() -> service.update(7L, 10L, request)).isInstanceOf(CommunityException.class);
        assertThat(post.getTitle()).isEqualTo("원본 제목");
        assertThat(post.getUpdatedAt()).isNull();
        verify(links, never()).deleteAllByPostId(anyLong());
    }

    @Test void deletingHiddenOwnPostPreservesModerationAndRejectsRepeat() {
        CommunityPost post = owned("TRAVEL");
        post.hide(99L, LocalDateTime.now(clock).minusDays(1));
        service.delete(7L, 10L);
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getHiddenByAdminUserId()).isEqualTo(99L);
        verify(images).deleteForPost(10L);
        verify(links).deleteAllByPostId(10L);
        assertThatThrownBy(() -> service.delete(7L, 10L)).isInstanceOf(CommunityException.class);
    }

    @Test void deletingPublicPostAlsoHidesItForOldBinaryRollbackWithoutForgingAdminHistory() {
        CommunityPost post = owned("TRAVEL");
        service.delete(7L, 10L);
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.isHidden()).isTrue();
        assertThat(post.getHiddenAt()).isNull();
        assertThat(post.getHiddenByAdminUserId()).isNull();
    }

    private CommunityPost owned(String category) {
        CommunityPost post = CommunityPost.create(category, "원본 제목", "원본 본문", 7L);
        ReflectionTestUtils.setField(post, "id", 10L);
        when(posts.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
        return post;
    }
    private CommunityPostUpdateRequest request(String json) throws Exception {
        return mapper.readValue(json, CommunityPostUpdateRequest.class);
    }
}
