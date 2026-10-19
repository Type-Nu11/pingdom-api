package com.typenull.pingdom.place.api.dto.review;

import static org.assertj.core.api.Assertions.assertThat;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceReviewUpdateRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsOmittedFieldsAndEmptyPhotoReplacement() {
        assertThat(validator.validate(new PlaceReviewUpdateRequest(null, null, null, null, null))).isEmpty();
        assertThat(validator.validate(new PlaceReviewUpdateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN), "수정", List.of(), null))).isEmpty();
    }

    @Test
    void rejectsInvalidRecommendationAndPhotoContracts() {
        List<PlaceReviewUpdateRequest> requests = List.of(
                new PlaceReviewUpdateRequest("  ", null, null, null, null),
                new PlaceReviewUpdateRequest("기존", List.of(PlaceReviewRecommendReason.CLEAN), null, null, null),
                new PlaceReviewUpdateRequest(null, List.of(), null, null, null),
                new PlaceReviewUpdateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.CLEAN), null, null, null),
                new PlaceReviewUpdateRequest(null, null, "   ", null, null),
                new PlaceReviewUpdateRequest(null, null, "a".repeat(2001), null, null),
                new PlaceReviewUpdateRequest(null, null, null, List.of(1L, 1L), null),
                new PlaceReviewUpdateRequest(null, null, null, List.of(0L), null),
                new PlaceReviewUpdateRequest(null, null, null, Arrays.asList((Long) null), null),
                new PlaceReviewUpdateRequest(null, null, null, List.of(1L, 2L, 3L, 4L), null),
                new PlaceReviewUpdateRequest(null, null, null, null, List.of("https://evil.test/image")));
        for (PlaceReviewUpdateRequest request : requests) {
            assertThat(validator.validate(request)).as(request.toString()).isNotEmpty();
        }
    }
}
