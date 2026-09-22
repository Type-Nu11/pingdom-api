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

    /**
     * 미디어 저장과 S3 검증·삭제 예약을 모의로 관찰할 서비스를 준비.
     */
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

    /**
     * 장소 소유자가 아닌 사용자의 탐색 미디어 등록을 거절하고 저장하지 않는지 확인.
     */
    @Test
    void rejectsNonOwnerExplorationMedia() {
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

    /**
     * 순서 누락 시 기존 최대 순서 다음 값을 부여하고 요청 URL 대신 검증한 S3 키의 공개 URL을 반환하는지 확인.
     */
    @Test
    void assignsNextExplorationOrder() {
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

    /**
     * 다른 장소 prefix의 키는 S3 조회와 저장 전에 거절하는지 확인.
     */
    @Test
    void rejectsOtherPlaceMediaKey() {
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

    /**
     * HEAD 메타데이터가 10MiB를 1바이트 넘으면 요청을 거절하고 미디어를 저장하지 않는지 확인.
     */
    @Test
    void rejectsOversizedExplorationObject() {
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

    /**
     * 객체 메타데이터가 PDF이면 탐색 이미지로 등록할 수 없는지 확인.
     */
    @Test
    void rejectsUnsupportedExplorationMime() {
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

    /**
     * 새 게시물 이미지의 URL·장소·원본 ID로 VERIFICATION 미디어 한 건을 저장하는지 확인.
     */
    @Test
    void recordsMapImageVerification() {
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

    /**
     * 이미 원본 게시물 ID로 미디어가 있으면 다시 저장하지 않는지 확인.
     */
    @Test
    void skipsRecordedVerificationImage() {
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

    /**
     * 공개 장소의 EXPLORATION 조회 결과를 해당 장소의 미디어 응답으로 매핑하는지 확인.
     */
    @Test
    void loadsVisibleExplorationMedia() {
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

    /**
     * 검색 비노출 장소는 탐색 미디어 조회에서도 PLACE_NOT_FOUND로 처리하는지 확인.
     */
    @Test
    void rejectsHiddenExplorationPlace() {
        MapPlace place = place(1L, 7L);
        place.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        when(mapPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        assertThatThrownBy(() -> placeMediaService.getExplorationMedia(1L))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_NOT_FOUND));
    }

    /**
     * EXPLORATION 범위에 해당 ID가 없으면 다른 용도의 미디어를 삭제하지 않고 미디어 없음 오류를 반환하는지 확인.
     */
    @Test
    void rejectsMissingExplorationMedia() {
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

    /**
     * 탐색 미디어 행을 삭제하면서 해당 S3 키와 삭제 사유를 Outbox publisher에 전달하는지 확인.
     */
    @Test
    void queuesExplorationObjectDeletion() {
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

    /**
     * 원본 게시물 10에 연결된 기존 검증 미디어를 생성.
     */
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

    /**
     * ID와 등록자를 지정한 공개·운영 장소 fixture를 생성.
     */
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
