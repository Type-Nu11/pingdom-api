package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestCreateRequest;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class MerchantPlaceReviewModerationServiceTest {

    @Test
    void listReturnsVisibleHiddenAndDeletedReviewsWithOnlyTheLatestDeletionRequest() {
        MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        PlaceReviewDeletionRequestRepository deletionRequestRepository = mock(PlaceReviewDeletionRequestRepository.class);
        MerchantPlaceReviewModerationService service = new MerchantPlaceReviewModerationService(
                ownerPlaceRepository, reviewRepository, deletionRequestRepository, Clock.systemUTC());
        PlaceReview pendingReview = review(10L, 1L, PlaceReviewVisibilityStatus.HIDDEN);
        PlaceReview rejectedReview = review(11L, 1L, PlaceReviewVisibilityStatus.HIDDEN);
        PlaceReview deletedReview = review(12L, 1L, PlaceReviewVisibilityStatus.DELETED);
        PlaceReview reviewWithoutRequest = review(13L, 1L, PlaceReviewVisibilityStatus.VISIBLE);
        PlaceReviewDeletionRequest pendingRequest = deletionRequest(
                102L,
                pendingReview,
                PlaceReviewDeletionRequestStatus.PENDING,
                LocalDateTime.of(2026, 8, 31, 14, 0),
                null,
                null
        );
        PlaceReviewDeletionRequest latestRejectedRequest = deletionRequest(
                101L,
                rejectedReview,
                PlaceReviewDeletionRequestStatus.REJECTED,
                LocalDateTime.of(2026, 8, 31, 12, 0),
                LocalDateTime.of(2026, 8, 31, 13, 0),
                "검토 결과 삭제 사유가 부족합니다."
        );
        PlaceReviewDeletionRequest olderApprovedRequest = deletionRequest(
                100L,
                rejectedReview,
                PlaceReviewDeletionRequestStatus.APPROVED,
                LocalDateTime.of(2026, 8, 30, 12, 0),
                LocalDateTime.of(2026, 8, 30, 13, 0),
                null
        );
        PlaceReviewDeletionRequest approvedRequest = deletionRequest(
                103L,
                deletedReview,
                PlaceReviewDeletionRequestStatus.APPROVED,
                LocalDateTime.of(2026, 8, 31, 15, 0),
                LocalDateTime.of(2026, 8, 31, 16, 0),
                null
        );
        when(ownerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findAllByPlace_IdAndVisibilityStatusIn(eq(1L), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(pendingReview, rejectedReview, deletedReview, reviewWithoutRequest),
                        org.springframework.data.domain.PageRequest.of(1, 10),
                        14
                ));
        when(deletionRequestRepository.findAllByReview_IdInOrderByReview_IdAscCreatedAtDescIdDesc(List.of(10L, 11L, 12L, 13L)))
                .thenReturn(List.of(pendingRequest, latestRejectedRequest, olderApprovedRequest, approvedRequest));

        var response = service.list(7L, 1L, 2, 10);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.limit()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(14);
        assertThat(response.reviews()).hasSize(4);
        assertThat(response.reviews().get(0).visibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.HIDDEN);
        assertThat(response.reviews().get(0).deletionRequest().deletionRequestId()).isEqualTo(102L);
        assertThat(response.reviews().get(0).deletionRequest().status()).isEqualTo(PlaceReviewDeletionRequestStatus.PENDING);
        assertThat(response.reviews().get(1).deletionRequest().deletionRequestId()).isEqualTo(101L);
        assertThat(response.reviews().get(1).deletionRequest().status()).isEqualTo(PlaceReviewDeletionRequestStatus.REJECTED);
        assertThat(response.reviews().get(1).deletionRequest().reviewNote()).isEqualTo("검토 결과 삭제 사유가 부족합니다.");
        assertThat(response.reviews().get(2).visibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.DELETED);
        assertThat(response.reviews().get(2).deletionRequest().status()).isEqualTo(PlaceReviewDeletionRequestStatus.APPROVED);
        assertThat(response.reviews().get(3).deletionRequest()).isNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findAllByPlace_IdAndVisibilityStatusIn(
                eq(1L),
                eq(List.of(
                        PlaceReviewVisibilityStatus.VISIBLE,
                        PlaceReviewVisibilityStatus.HIDDEN,
                        PlaceReviewVisibilityStatus.DELETED
                )),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

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

    private PlaceReview review(Long reviewId, Long placeId, PlaceReviewVisibilityStatus visibilityStatus) {
        PlaceReview review = mock(PlaceReview.class);
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(placeId);
        when(review.getId()).thenReturn(reviewId);
        when(review.getPlace()).thenReturn(place);
        when(review.getUserId()).thenReturn(20L);
        when(review.getRecommendReason()).thenReturn("추천 이유");
        when(review.getContent()).thenReturn("리뷰 내용");
        when(review.getImageUrls()).thenReturn(List.of());
        when(review.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 10, 0));
        when(review.getVisibilityStatus()).thenReturn(visibilityStatus);
        return review;
    }

    private PlaceReviewDeletionRequest deletionRequest(
            Long deletionRequestId,
            PlaceReview review,
            PlaceReviewDeletionRequestStatus status,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt,
            String reviewNote
    ) {
        PlaceReviewDeletionRequest request = mock(PlaceReviewDeletionRequest.class);
        when(request.getId()).thenReturn(deletionRequestId);
        when(request.getReview()).thenReturn(review);
        when(request.getStatus()).thenReturn(status);
        when(request.getCreatedAt()).thenReturn(requestedAt);
        when(request.getReviewedAt()).thenReturn(reviewedAt);
        when(request.getReviewNote()).thenReturn(reviewNote);
        return request;
    }
}
