package com.typenull.pingdom.place.api.dto.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewResponseImageSnapshotTest {

    /** 공개·내 리뷰·사업자·삭제 심사 응답을 만든 뒤 원본 이미지 목록을 비워도 각 응답이 이미지를 유지하는지 확인. */
    @Test
    void responseMappingsSnapshotReviewImages() {
        List<String> imageUrls = new ArrayList<>(List.of("https://cdn.pingdom.test/review.jpg"));
        MapPlace place = mock(MapPlace.class);
        PlaceReview review = mock(PlaceReview.class);
        PlaceReviewDeletionRequest deletionRequest = mock(PlaceReviewDeletionRequest.class);
        when(place.getId()).thenReturn(1L);
        when(review.getPlace()).thenReturn(place);
        when(review.getImageUrls()).thenReturn(imageUrls);
        when(deletionRequest.getReview()).thenReturn(review);

        PlaceReviewResponse publicResponse = PlaceReviewResponse.from(review);
        MyPlaceReviewResponse myResponse = MyPlaceReviewResponse.from(review);
        MerchantPlaceReviewResponse merchantResponse = MerchantPlaceReviewResponse.from(review, null);
        AdminPlaceReviewDeletionRequestResponse adminResponse =
                AdminPlaceReviewDeletionRequestResponse.from(deletionRequest);

        imageUrls.clear();

        assertThat(publicResponse.imageUrls()).containsExactly("https://cdn.pingdom.test/review.jpg");
        assertThat(myResponse.imageUrls()).containsExactly("https://cdn.pingdom.test/review.jpg");
        assertThat(merchantResponse.imageUrls()).containsExactly("https://cdn.pingdom.test/review.jpg");
        assertThat(adminResponse.imageUrls()).containsExactly("https://cdn.pingdom.test/review.jpg");
    }
}
