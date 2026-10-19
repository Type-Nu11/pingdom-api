package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewUpdateRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.*;
import com.typenull.pingdom.place.infrastructure.persistence.place.*;
import com.typenull.pingdom.shared.exception.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaceReviewManagementServiceTest {
    private final PlaceReviewRepository reviews = mock(PlaceReviewRepository.class);
    private final PlaceReviewMediaService media = mock(PlaceReviewMediaService.class);
    private final PlaceReviewDeletionRequestRepository requests = mock(PlaceReviewDeletionRequestRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), ZoneOffset.UTC);
    private final PlaceReviewService service = new PlaceReviewService(mock(MapPlaceRepository.class), reviews, media, requests, clock);

    @Test
    void editsHiddenReviewWithoutRestoringVisibilityAndKeepsOmittedFields() {
        PlaceReview review = ownReview();
        review.hide(LocalDateTime.now(clock));
        var response = service.update(7L, 10L, 1L, new PlaceReviewUpdateRequest(null, null, " 수정 내용 ", null, null));
        assertThat(response.content()).isEqualTo("수정 내용");
        assertThat(response.recommendReasons()).containsExactly(PlaceReviewRecommendReason.FRIENDLY);
        assertThat(review.getVisibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.HIDDEN);
    }

    @Test
    void replacesRecommendationOrderAndLegacyRepresentative() {
        PlaceReview review = ownReview();
        service.update(7L, 10L, 1L, new PlaceReviewUpdateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.FRIENDLY), null, null, null));
        assertThat(review.getRecommendReason()).isEqualTo("CLEAN");
        assertThat(review.getRecommendReasons()).containsExactly(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.FRIENDLY);
        verify(reviews).flush();
        service.update(7L, 10L, 1L, new PlaceReviewUpdateRequest("레거시 이유", null, null, null, null));
        assertThat(review.getRecommendReasons()).isEmpty();
        assertThat(review.getRecommendReason()).isEqualTo("레거시 이유");
    }

    @Test
    void hidesExistenceForOthersMissingAndDeletedReviews() {
        PlaceReview review = ownReview();
        assertCode(() -> service.delete(9L, 10L, 1L), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        assertCode(() -> service.update(9L, 10L, 1L, empty()), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        assertCode(() -> service.delete(7L, 11L, 1L), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        review.markDeleted(LocalDateTime.now(clock));
        assertCode(() -> service.update(7L, 10L, 1L, empty()), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        verifyNoInteractions(media);
    }

    @Test
    void rejectsPendingModerationBeforeAnyMutation() {
        PlaceReview review = ownReview();
        when(requests.existsByReview_IdAndStatus(1L, PlaceReviewDeletionRequestStatus.PENDING)).thenReturn(true);
        assertCode(() -> service.update(7L, 10L, 1L, new PlaceReviewUpdateRequest(null, null, "변경", List.of(), null)), MapErrorCode.PLACE_REVIEW_UPDATE_PENDING_DELETION);
        assertThat(review.getContent()).isEqualTo("원본");
        verifyNoInteractions(media);
    }

    @Test
    void failedPhotoValidationLeavesContentAndReasonsUntouched() {
        PlaceReview review = ownReview();
        doThrow(new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_EXPIRED)).when(media).replace(7L, 10L, review, List.of(5L));
        assertCode(() -> service.update(7L, 10L, 1L, new PlaceReviewUpdateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN), "변경", List.of(5L), null)), MapErrorCode.PLACE_REVIEW_MEDIA_EXPIRED);
        assertThat(review.getContent()).isEqualTo("원본");
        assertThat(review.getRecommendReasons()).containsExactly(PlaceReviewRecommendReason.FRIENDLY);
    }

    @Test
    void repeatedOwnDeletionPreservesEvidenceAndFirstDeletionTime() {
        PlaceReview review = ownReview();
        review.hide(LocalDateTime.now(clock));
        service.delete(7L, 10L, 1L);
        LocalDateTime deletedAt = review.getDeletedAt();
        service.delete(7L, 10L, 1L);
        assertThat(review.getVisibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.DELETED);
        assertThat(review.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(review.getContent()).isEqualTo("원본");
        assertThat(review.getImageUrls()).containsExactly("https://cdn.test/legacy.jpg");
        verifyNoInteractions(media, requests);
    }

    private PlaceReview ownReview() {
        MapPlace place = mock(MapPlace.class);
        when(place.getId()).thenReturn(10L);
        PlaceReview review = PlaceReview.create(place, 7L, "FRIENDLY", List.of(PlaceReviewRecommendReason.FRIENDLY), "원본", List.of("https://cdn.test/legacy.jpg"), LocalDateTime.now(clock));
        ReflectionTestUtils.setField(review, "id", 1L);
        when(reviews.findByIdAndPlaceIdForUpdate(1L, 10L)).thenReturn(Optional.of(review));
        return review;
    }

    private PlaceReviewUpdateRequest empty() { return new PlaceReviewUpdateRequest(null, null, null, null, null); }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, MapErrorCode code) {
        assertThatThrownBy(action).isInstanceOf(MapException.class).extracting(e -> ((MapException) e).getErrorCode()).isEqualTo(code);
    }
}
