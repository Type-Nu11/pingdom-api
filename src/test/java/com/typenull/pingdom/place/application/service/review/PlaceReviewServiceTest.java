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
import org.springframework.data.domain.PageImpl;
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
}
