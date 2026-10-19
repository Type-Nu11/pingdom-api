package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewDeletionRequestReviewRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewDeletionRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminPlaceReviewDeletionRequestServiceTest {

    /**
     * 활성 관리자가 삭제 요청을 승인하면 응답 상태가 APPROVED이고 리뷰에 고정 시각의 삭제 표시를 요청하는지 확인.
     */
    @Test
    void approvesReviewDeletion() {
        PlaceReviewDeletionRequestRepository deletionRequestRepository = mock(PlaceReviewDeletionRequestRepository.class);
        PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T03:00:00Z"), ZoneOffset.UTC);
        AdminPlaceReviewDeletionRequestService service = new AdminPlaceReviewDeletionRequestService(
                deletionRequestRepository, reviewRepository, userRepository, clock);
        User admin = User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        PlaceReview review = mock(PlaceReview.class);
        MapPlace place = mock(MapPlace.class);
        when(review.getId()).thenReturn(10L);
        when(review.getPlace()).thenReturn(place);
        when(place.getId()).thenReturn(1L);
        when(review.getImageUrls()).thenReturn(List.of());
        PlaceReviewDeletionRequest deletionRequest = PlaceReviewDeletionRequest.submit(
                review, 1L, "개인정보가 포함되어 있습니다.", java.time.LocalDateTime.now(clock));
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(deletionRequestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(deletionRequest));
        when(reviewRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(review));

        var response = service.review(9L, 5L, new PlaceReviewDeletionRequestReviewRequest(
                PlaceReviewDeletionRequestStatus.APPROVED, "정책 위반 확인"));

        assertThat(response.status()).isEqualTo(PlaceReviewDeletionRequestStatus.APPROVED);
        verify(review).markDeleted(java.time.LocalDateTime.now(clock));
    }
    @Test
    void approvesPendingRequestAfterAuthorAlreadyDeletedReview() {
        PlaceReviewDeletionRequestRepository requests = mock(PlaceReviewDeletionRequestRepository.class);
        PlaceReviewRepository reviews = mock(PlaceReviewRepository.class);
        UserRepository users = mock(UserRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);
        var service = new AdminPlaceReviewDeletionRequestService(requests, reviews, users, clock);
        User admin = User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        PlaceReview review = mock(PlaceReview.class);
        MapPlace place = mock(MapPlace.class);
        when(review.getId()).thenReturn(10L);
        when(review.getPlace()).thenReturn(place);
        when(place.getId()).thenReturn(1L);
        when(review.getImageUrls()).thenReturn(List.of());
        when(review.getVisibilityStatus()).thenReturn(com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus.DELETED);
        var request = PlaceReviewDeletionRequest.submit(review, 1L, "심사 증거", java.time.LocalDateTime.now(clock));
        when(users.findById(9L)).thenReturn(Optional.of(admin));
        when(requests.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(reviews.findByIdForUpdate(10L)).thenReturn(Optional.of(review));
        assertThat(service.review(9L, 5L, new PlaceReviewDeletionRequestReviewRequest(PlaceReviewDeletionRequestStatus.APPROVED, null)).status())
                .isEqualTo(PlaceReviewDeletionRequestStatus.APPROVED);
        verify(review, never()).markDeleted(any());
    }
}
