package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaCreateRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceMediaServiceTest {

    private final MapPlaceRepository mapPlaceRepository = org.mockito.Mockito.mock(MapPlaceRepository.class);
    private final PlaceMediaRepository placeMediaRepository = org.mockito.Mockito.mock(PlaceMediaRepository.class);
    private PlaceMediaService placeMediaService;

    @BeforeEach
    void setUp() {
        placeMediaService = new PlaceMediaService(mapPlaceRepository, placeMediaRepository);
    }

    @Test
    void createExplorationMediaRequiresPlaceOwner() {
        when(mapPlaceRepository.findById(1L)).thenReturn(Optional.of(place(1L, 99L)));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                "https://cdn.pingdom.test/place.jpg",
                null,
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
        when(mapPlaceRepository.findById(1L)).thenReturn(Optional.of(place));
        when(placeMediaRepository.findMaxDisplayOrder(1L, PlaceMediaPurpose.EXPLORATION)).thenReturn(2);
        when(placeMediaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PlaceMediaCreateRequest request = new PlaceMediaCreateRequest(
                "https://cdn.pingdom.test/place.jpg",
                "place/original.jpg",
                "https://cdn.pingdom.test/place-thumb.jpg",
                "place/thumb.jpg",
                null
        );

        PlaceMediaItem response = placeMediaService.createExplorationMedia(1L, 7L, request);

        assertThat(response.placeId()).isEqualTo(1L);
        assertThat(response.purpose()).isEqualTo(PlaceMediaPurpose.EXPLORATION);
        assertThat(response.displayOrder()).isEqualTo(3);
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
