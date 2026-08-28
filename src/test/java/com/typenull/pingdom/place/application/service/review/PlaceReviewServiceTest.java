package com.typenull.pingdom.place.application.service.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
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

    @Test
    void publicListLoadsOnlyVisibleReviews() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewService service = new PlaceReviewService(placeRepository, reviewRepository, Clock.systemUTC());
        when(placeRepository.existsById(1L)).thenReturn(true);
        when(reviewRepository.findAllByPlace_IdAndVisibilityStatus(eq(1L), eq(PlaceReviewVisibilityStatus.VISIBLE), any()))
                .thenReturn(new PageImpl<PlaceReview>(java.util.List.of()));

        service.list(1L, 1, 20);

        verify(reviewRepository).findAllByPlace_IdAndVisibilityStatus(
                eq(1L), eq(PlaceReviewVisibilityStatus.VISIBLE), any());
    }

    @Test
    void myReviewListIncludesVisibleAndHiddenReviewsButExcludesDeletedReviews() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewService service = new PlaceReviewService(placeRepository, reviewRepository, Clock.systemUTC());
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
