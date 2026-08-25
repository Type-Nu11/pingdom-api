package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MerchantPlaceReviewModerationServiceTest {

    @Test
    void otherMerchantCannotHideOrRequestDeletionForReview() {
        MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewDeletionRequestRepository deletionRequestRepository = mock(PlaceReviewDeletionRequestRepository.class);
        MerchantPlaceReviewModerationService service = new MerchantPlaceReviewModerationService(
                ownerPlaceRepository, reviewRepository, deletionRequestRepository, Clock.systemUTC());
        when(ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.requestDeletion(
                99L, 1L, 10L, new PlaceReviewDeletionRequestCreateRequest("부적절한 리뷰입니다.")))
                .isInstanceOf(MapException.class);
    }

    @Test
    void pendingDeletionRequestCannotBeDuplicated() {
        MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewDeletionRequestRepository deletionRequestRepository = mock(PlaceReviewDeletionRequestRepository.class);
        MerchantPlaceReviewModerationService service = new MerchantPlaceReviewModerationService(
                ownerPlaceRepository, reviewRepository, deletionRequestRepository, Clock.systemUTC());
        when(ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(1L, 1L)).thenReturn(true);
        when(reviewRepository.findByIdAndPlaceIdForUpdate(10L, 1L)).thenReturn(Optional.of(mock(PlaceReview.class)));
        when(deletionRequestRepository.existsByReview_IdAndStatus(10L, PlaceReviewDeletionRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestDeletion(
                1L, 1L, 10L, new PlaceReviewDeletionRequestCreateRequest("명예를 훼손하는 내용입니다.")))
                .isInstanceOf(MapException.class);
        verify(deletionRequestRepository).existsByReview_IdAndStatus(10L, PlaceReviewDeletionRequestStatus.PENDING);
    }
}
