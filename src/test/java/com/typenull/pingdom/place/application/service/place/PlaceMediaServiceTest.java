package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaCreateRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaResponse;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectMetadata;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceMediaServiceTest {

    private final MapPlaceRepository mapPlaceRepository = org.mockito.Mockito.mock(MapPlaceRepository.class);
    private final PlaceMediaRepository placeMediaRepository = org.mockito.Mockito.mock(PlaceMediaRepository.class);
    private final S3ObjectStorage s3ObjectStorage = org.mockito.Mockito.mock(S3ObjectStorage.class);
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher =
            org.mockito.Mockito.mock(S3ObjectDeleteOutboxPublisher.class);
    private PlaceMediaService placeMediaService;

    @BeforeEach
    void setUp() {
        placeMediaService = new PlaceMediaService(
                mapPlaceRepository,
                placeMediaRepository,
                null,
                s3ObjectStorage,
                s3ObjectDeleteOutboxPublisher
        );
    }

    @Test
    void createExplorationMediaRequiresPlaceOwner() {
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place(1L, 99L)));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                "https://cdn.pingdom.test/place.jpg",
                "places/1/exploration/7/issued.jpg",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> placeMediaService.createExplorationMedia(1L, 7L, request))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.OTHERS_PLACE_MEDIA_NOT_MANAGED));

        verify(placeMediaRepository, never()).save(any());
    }

    @Test
    void createExplorationMediaUsesNextDisplayOrderWhenOrderIsMissing() {
        MapPlace place = place(1L, 7L);
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place));
        when(placeMediaRepository.findMaxDisplayOrder(1L, PlaceMediaPurpose.EXPLORATION)).thenReturn(2);
        when(placeMediaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3ObjectStorage.headObject("places/1/exploration/7/issued.jpg"))
                .thenReturn(new S3ObjectMetadata(1_024L, "image/jpeg"));
        when(s3ObjectStorage.publicUrl("places/1/exploration/7/issued.jpg"))
                .thenReturn("https://s3.pingdom.test/places/1/exploration/7/issued.jpg");
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                "https://untrusted.example/place.jpg",
                "places/1/exploration/7/issued.jpg",
                "https://cdn.pingdom.test/place-thumb.jpg",
                "place/thumb.jpg",
                null
        );

        PlaceMediaItem response = placeMediaService.createExplorationMedia(1L, 7L, request);

        assertThat(response.placeId()).isEqualTo(1L);
        assertThat(response.purpose()).isEqualTo(PlaceMediaPurpose.EXPLORATION);
        assertThat(response.displayOrder()).isEqualTo(3);
        assertThat(response.imageUrl()).isEqualTo("https://s3.pingdom.test/places/1/exploration/7/issued.jpg");
    }

    @Test
    void createExplorationMediaRejectsKeyIssuedForAnotherPlace() {
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place(1L, 7L)));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                null,
                "places/2/exploration/7/issued.jpg",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> placeMediaService.createExplorationMedia(1L, 7L, request))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));

        verify(s3ObjectStorage, never()).headObject(any());
        verify(placeMediaRepository, never()).save(any());
    }

    @Test
    void createExplorationMediaRejectsObjectLargerThanLimit() {
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place(1L, 7L)));
        when(s3ObjectStorage.headObject("places/1/exploration/7/oversized.jpg"))
                .thenReturn(new S3ObjectMetadata(10L * 1024 * 1024 + 1, "image/jpeg"));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                null,
                "places/1/exploration/7/oversized.jpg",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> placeMediaService.createExplorationMedia(1L, 7L, request))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));

        verify(placeMediaRepository, never()).save(any());
    }

    @Test
    void createExplorationMediaRejectsUnsupportedObjectContentType() {
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place(1L, 7L)));
        when(s3ObjectStorage.headObject("places/1/exploration/7/invalid-type.jpg"))
                .thenReturn(new S3ObjectMetadata(1_024L, "application/pdf"));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                null,
                "places/1/exploration/7/invalid-type.jpg",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> placeMediaService.createExplorationMedia(1L, 7L, request))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));

        verify(placeMediaRepository, never()).save(any());
    }

    @Test
    void recordVerificationMediaCreatesMediaFromMapImageOnce() {
        MapPlace place = place(1L, 7L);
        MapImage mapImage = MapImage.builder()
                .id(10L)
                .imageUrl("https://cdn.pingdom.test/post.jpg")
                .s3Key("map/post.jpg")
                .thumbnailUrl("https://cdn.pingdom.test/post-thumb.jpg")
                .thumbnailS3Key("map/post-thumb.jpg")
                .mapPlace(place)
                .createdAt(LocalDateTime.of(2026, 7, 21, 10, 0))
                .build();
        when(placeMediaRepository.findBySourceMapImageId(10L)).thenReturn(Optional.empty());

        placeMediaService.recordVerificationMedia(mapImage);

        ArgumentCaptor<PlaceMedia> captor = ArgumentCaptor.forClass(PlaceMedia.class);
        verify(placeMediaRepository).save(captor.capture());
        PlaceMedia saved = captor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(PlaceMediaPurpose.VERIFICATION);
        assertThat(saved.getPlace()).isEqualTo(place);
        assertThat(saved.getSourceMapImageId()).isEqualTo(10L);
        assertThat(saved.getImageUrl()).isEqualTo("https://cdn.pingdom.test/post.jpg");
    }

    @Test
    void recordVerificationMediaSkipsAlreadyRecordedMapImage() {
        MapImage mapImage = MapImage.builder()
                .id(10L)
                .imageUrl("https://cdn.pingdom.test/post.jpg")
                .s3Key("map/post.jpg")
                .mapPlace(place(1L, 7L))
                .build();
        when(placeMediaRepository.findBySourceMapImageId(10L)).thenReturn(Optional.of(existingVerification()));

        placeMediaService.recordVerificationMedia(mapImage);

        verify(placeMediaRepository, never()).save(any());
    }

    @Test
    void getExplorationMediaReturnsOnlyExplorationMediaForVisiblePlace() {
        MapPlace place = place(1L, 7L);
        PlaceMedia exploration = PlaceMedia.exploration(
                place,
                "https://cdn.pingdom.test/exploration.jpg",
                null,
                null,
                null,
                0,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
        when(mapPlaceRepository.findById(1L)).thenReturn(Optional.of(place));
        when(placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                1L,
                PlaceMediaPurpose.EXPLORATION
        )).thenReturn(List.of(exploration));

        PlaceMediaResponse response = placeMediaService.getExplorationMedia(1L);

        assertThat(response.placeId()).isEqualTo(1L);
        assertThat(response.media()).hasSize(1);
        assertThat(response.media().get(0).purpose()).isEqualTo(PlaceMediaPurpose.EXPLORATION);
    }

    @Test
    void getExplorationMediaRejectsHiddenDiscoveryPlace() {
        MapPlace place = place(1L, 7L);
        place.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        when(mapPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        assertThatThrownBy(() -> placeMediaService.getExplorationMedia(1L))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    void deleteExplorationMediaRejectsVerificationMediaId() {
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place(1L, 7L)));
        when(placeMediaRepository.findByIdAndPlace_IdAndPurpose(
                10L,
                1L,
                PlaceMediaPurpose.EXPLORATION
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeMediaService.deleteExplorationMedia(1L, 10L, 7L))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_NOT_FOUND));
    }

    @Test
    void deleteExplorationMediaPublishesS3DeleteOutboxEvent() {
        MapPlace place = place(1L, 7L);
        PlaceMedia media = PlaceMedia.exploration(
                place,
                "https://s3.pingdom.test/places/1/exploration/7/issued.jpg",
                "places/1/exploration/7/issued.jpg",
                null,
                null,
                0,
                LocalDateTime.of(2026, 8, 25, 10, 0)
        );
        when(mapPlaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(place));
        when(placeMediaRepository.findByIdAndPlace_IdAndPurpose(10L, 1L, PlaceMediaPurpose.EXPLORATION))
                .thenReturn(Optional.of(media));

        placeMediaService.deleteExplorationMedia(1L, 10L, 7L);

        verify(placeMediaRepository).delete(media);
        verify(s3ObjectDeleteOutboxPublisher).publish(
                "places/1/exploration/7/issued.jpg",
                "PLACE_MEDIA",
                "10",
                "EXPLORATION_MEDIA_DELETED"
        );
    }

    private PlaceMedia existingVerification() {
        return PlaceMedia.verification(
                place(1L, 7L),
                "https://cdn.pingdom.test/post.jpg",
                "map/post.jpg",
                null,
                null,
                10L,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
    }

    private MapPlace place(Long id, Long userId) {
        return MapPlace.builder()
                .id(id)
                .name("테스트 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(userId)
                .registrant("tester")
                .build();
    }
}
