package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewCreateRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;

class PlaceReviewServiceTest {

    /**
     * 복수 추천 사유의 순서와 대표 사유를 유지하고 업로드 ID 목록을 미디어 연결 서비스로 전달하는지 확인.
     */
    @Test
    void createsStructuredReview() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewMediaService reviewMediaService = mock(PlaceReviewMediaService.class);
        PlaceReviewService service = new PlaceReviewService(placeRepository, reviewRepository, reviewMediaService, Clock.systemUTC());
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(10L);
        when(placeRepository.findById(10L)).thenReturn(java.util.Optional.of(place));
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlaceReviewCreateRequest request = new PlaceReviewCreateRequest(
                null,
                List.of(PlaceReviewRecommendReason.FRIENDLY, PlaceReviewRecommendReason.MULTILINGUAL_SUPPORT),
                "친절하고 영어 안내가 있어요.",
                List.of(5L, 3L),
                List.of()
        );

        var response = service.create(7L, 10L, request);

        assertThat(response.recommendReason()).isEqualTo("FRIENDLY");
        assertThat(response.recommendReasons()).containsExactly(
                PlaceReviewRecommendReason.FRIENDLY, PlaceReviewRecommendReason.MULTILINGUAL_SUPPORT);
        assertThat(response.imageUrls()).isEmpty();
        verify(reviewMediaService).connect(eq(7L), eq(10L), any(PlaceReview.class), eq(List.of(5L, 3L)));
    }

    /**
     * 공개 리뷰 목록이 VISIBLE 조건으로만 저장소를 조회하는지 확인.
     */
    @Test
    void loadsVisiblePublicReviews() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewMediaService reviewMediaService = mock(PlaceReviewMediaService.class);
        PlaceReviewService service = new PlaceReviewService(placeRepository, reviewRepository, reviewMediaService, Clock.systemUTC());
        when(placeRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.findAllByPlace_IdAndVisibilityStatus(eq(1L), eq(PlaceReviewVisibilityStatus.VISIBLE), any()))
                .thenReturn(new PageImpl<PlaceReview>(java.util.List.of()));

        service.list(1L, 1, 20);

        verify(reviewRepository).findAllByPlace_IdAndVisibilityStatus(
                eq(1L), eq(PlaceReviewVisibilityStatus.VISIBLE), any());
    }

    /**
     * 내 리뷰 조회가 공개·숨김만 포함하고 삭제는 제외하며 1부터 시작하는 페이지와 최신순 정렬을 유지하는지 확인.
     */
    @Test
    void listsOwnNonDeletedReviews() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewMediaService reviewMediaService = mock(PlaceReviewMediaService.class);
        PlaceReviewService service = new PlaceReviewService(placeRepository, reviewRepository, reviewMediaService, Clock.systemUTC());
        when(reviewRepository.findAllByUserIdAndVisibilityStatusIn(eq(7L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(1, 20), 23));

        var response = service.listMine(7L, 2, 20);

        Assertions.assertEquals(23, response.totalElements());
        Assertions.assertEquals(2, response.page());
        Assertions.assertEquals(20, response.limit());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findAllByUserIdAndVisibilityStatusIn(
                eq(7L),
                eq(List.of(PlaceReviewVisibilityStatus.VISIBLE, PlaceReviewVisibilityStatus.HIDDEN)),
                pageable.capture()
        );
        Assertions.assertEquals(1, pageable.getValue().getPageNumber());
        Assertions.assertEquals(20, pageable.getValue().getPageSize());
        Assertions.assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
        Assertions.assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("id").getDirection());
    }
}
