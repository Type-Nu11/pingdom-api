package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void adminApprovalMarksReviewAsDeleted() {
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
}
