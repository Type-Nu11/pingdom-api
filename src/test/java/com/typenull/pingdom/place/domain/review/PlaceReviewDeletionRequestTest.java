package com.typenull.pingdom.place.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceReviewDeletionRequestTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Test
    void rejectionRequiresReviewNote() {
        PlaceReviewDeletionRequest request = PlaceReviewDeletionRequest.submit(
                mock(PlaceReview.class), 1L, "욕설이 포함되어 있습니다.", now);

        assertThatThrownBy(() -> request.review(
                9L, PlaceReviewDeletionRequestStatus.REJECTED, " ", now.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewedRequestCannotBeReviewedAgain() {
        PlaceReviewDeletionRequest request = PlaceReviewDeletionRequest.submit(
                mock(PlaceReview.class), 1L, "개인정보가 포함되어 있습니다.", now);
        request.review(9L, PlaceReviewDeletionRequestStatus.APPROVED, null, now.plusMinutes(1));

        assertThat(request.getStatus()).isEqualTo(PlaceReviewDeletionRequestStatus.APPROVED);
        assertThatThrownBy(() -> request.review(
                10L, PlaceReviewDeletionRequestStatus.REJECTED, "재심사", now.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
