package com.typenull.pingdom.place.application.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUploadStatus;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewMediaUploadRepository;
import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.post.infrastructure.storage.image.ProcessedImageUpload;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class PlaceReviewMediaServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-16T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void uploadStoresProcessedImageAsTemporaryReviewMedia() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewMediaUploadRepository mediaRepository = mock(PlaceReviewMediaUploadRepository.class);
        ImageUploadProcessor processor = mock(ImageUploadProcessor.class);
        S3ObjectStorage objectStorage = mock(S3ObjectStorage.class);
        PlaceReviewMediaService service = service(placeRepository, mediaRepository, processor, objectStorage);
        MockMultipartFile file = new MockMultipartFile("file", "review.jpg", "image/jpeg", new byte[] {1, 2, 3});
        ProcessedImageUpload processed = new ProcessedImageUpload(
                new byte[] {9, 8, 7}, "review-processed.jpg", "image/jpeg",
                new byte[] {1}, "review-thumbnail.jpg", "image/jpeg", 100, 50, 100, 50);
        when(placeRepository.existsById(10L)).thenReturn(true);
        when(processor.process(file)).thenReturn(processed);
        when(objectStorage.put(any(byte[].class), eq("review-processed.jpg"), eq("image/jpeg"), eq("place-reviews")))
                .thenReturn(new S3ObjectStorage.S3PutResult("place-reviews/key", "https://cdn.test/review.jpg"));
        when(mediaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(7L, 10L, file);

        assertThat(response.imageUrl()).isEqualTo("https://cdn.test/review.jpg");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.fileSize()).isEqualTo(3);
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 17, 1, 0));
        ArgumentCaptor<PlaceReviewMediaUpload> mediaCaptor = ArgumentCaptor.forClass(PlaceReviewMediaUpload.class);
        verify(mediaRepository).save(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(mediaCaptor.getValue().getPlaceId()).isEqualTo(10L);
        assertThat(mediaCaptor.getValue().getStatus()).isEqualTo(PlaceReviewMediaUploadStatus.UPLOADED);
    }

    @Test
    void connectRejectsMediaUploadedForAnotherPlaceOrUser() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewMediaUploadRepository mediaRepository = mock(PlaceReviewMediaUploadRepository.class);
        PlaceReviewMediaService service = service(placeRepository, mediaRepository, mock(ImageUploadProcessor.class), mock(S3ObjectStorage.class));
        PlaceReviewMediaUpload media = PlaceReviewMediaUpload.upload(
                10L, 99L, "key", "https://cdn.test/review.jpg", "image/jpeg", 10,
                LocalDateTime.of(2026, 9, 17, 1, 0), LocalDateTime.of(2026, 9, 16, 1, 0));
        when(mediaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(media));
        PlaceReview review = review();

        assertThatThrownBy(() -> service.connect(7L, 10L, review, List.of(1L)))
                .isInstanceOf(MapException.class)
                .extracting(exception -> ((MapException) exception).getErrorCode())
                .isEqualTo(MapErrorCode.PLACE_REVIEW_MEDIA_FORBIDDEN);
    }

    @Test
    void connectPreservesRequestOrderAndMakesMediaSingleUse() {
        MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
        PlaceReviewMediaUploadRepository mediaRepository = mock(PlaceReviewMediaUploadRepository.class);
        PlaceReviewMediaService service = service(placeRepository, mediaRepository, mock(ImageUploadProcessor.class), mock(S3ObjectStorage.class));
        PlaceReviewMediaUpload first = upload(10L, 7L, "first");
        PlaceReviewMediaUpload second = upload(10L, 7L, "second");
        when(mediaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(mediaRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        PlaceReview review = review();

        service.connect(7L, 10L, review, List.of(2L, 1L));

        assertThat(review.getMediaUploads()).containsExactly(second, first);
        assertThat(review.getImageUrls()).containsExactly("https://cdn.test/second.jpg", "https://cdn.test/first.jpg");
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThatThrownBy(() -> service.connect(7L, 10L, review, List.of(1L)))
                .isInstanceOf(MapException.class)
                .extracting(exception -> ((MapException) exception).getErrorCode())
                .isEqualTo(MapErrorCode.PLACE_REVIEW_MEDIA_ALREADY_CONNECTED);
    }

    private PlaceReviewMediaService service(
            MapPlaceRepository placeRepository,
            PlaceReviewMediaUploadRepository mediaRepository,
            ImageUploadProcessor processor,
            S3ObjectStorage objectStorage
    ) {
        return new PlaceReviewMediaService(
                placeRepository,
                mediaRepository,
                processor,
                objectStorage,
                mock(S3ObjectDeleteOutboxPublisher.class),
                FIXED_CLOCK
        );
    }

    private PlaceReview review() {
        MapPlace place = mock(MapPlace.class);
        return PlaceReview.create(
                place, 7L, "FRIENDLY", List.of(PlaceReviewRecommendReason.FRIENDLY), "좋아요", List.of(),
                LocalDateTime.of(2026, 9, 16, 1, 0));
    }

    private PlaceReviewMediaUpload upload(Long placeId, Long userId, String key) {
        return PlaceReviewMediaUpload.upload(
                placeId, userId, key, "https://cdn.test/" + key + ".jpg", "image/jpeg", 10,
                LocalDateTime.of(2026, 9, 17, 1, 0), LocalDateTime.of(2026, 9, 16, 1, 0));
    }
}
