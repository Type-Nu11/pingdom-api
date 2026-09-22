package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMediaUpload;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMediaUploadStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMediaUploadRepository;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerPlaceManagementServiceTest {

    private static final Long PLACE_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private PlaceMediaRepository placeMediaRepository;
    @Mock private MerchantPlaceMediaUploadRepository mediaUploadRepository;
    @Mock private MerchantPlaceCapabilityPolicy capabilityPolicy;
    @Mock private PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    @Mock private S3ObjectStorage s3ObjectStorage;
    @Mock private S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

    private MerchantOwnerPlaceManagementService service;

    /**
     * 업로드 만료와 등록 시각을 검증할 수 있도록 고정 Clock과 모의 의존성을 가진 장소 관리 서비스를 생성.
     */
    @BeforeEach
    void setUp() {
        service = new MerchantOwnerPlaceManagementService(
                mapPlaceRepository,
                placeMediaRepository,
                mediaUploadRepository,
                capabilityPolicy,
                operatingHoursEvaluator,
                s3ObjectStorage,
                s3ObjectDeleteOutboxPublisher,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }

    /**
     * 업로드 URL 발급 시 저장하는 이력의 장소·발급자·만료 시각과 ISSUED 상태를 검증.
     */
    @Test
    void recordsMediaUploadIssuance() {
        MerchantOwnerMediaUploadRequest request = new MerchantOwnerMediaUploadRequest("store.jpg", "image/jpeg", 1_024L);
        LocalDateTime expiresAt = NOW.plusMinutes(10);
        when(mapPlaceRepository.findById(PLACE_ID)).thenReturn(Optional.of(place()));
        when(s3ObjectStorage.presignedPut(any(), any())).thenReturn(new S3ObjectStorage.PresignedPutResult(
                "places/10/exploration/20/new.jpg", "https://upload", "https://image", expiresAt
        ));

        service.createUploadUrl(USER_ID, PLACE_ID, request);

        ArgumentCaptor<MerchantPlaceMediaUpload> uploadCaptor = ArgumentCaptor.forClass(MerchantPlaceMediaUpload.class);
        verify(mediaUploadRepository).save(uploadCaptor.capture());
        MerchantPlaceMediaUpload upload = uploadCaptor.getValue();
        assertThat(upload.getPlaceId()).isEqualTo(PLACE_ID);
        assertThat(upload.getIssuedByUserId()).isEqualTo(USER_ID);
        assertThat(upload.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(upload.getStatus()).isEqualTo(MerchantPlaceMediaUploadStatus.ISSUED);
    }

    /**
     * 유효한 발급 이력과 업로드된 객체로 미디어를 등록하면 공개 URL·키·다음 순서가 응답에 담기는지 검증.
     * 발급 이력도 현재 시각의 REGISTERED 상태로 전환되는지 확인.
     */
    @Test
    void registersIssuedMediaObject() {
        String s3Key = "places/10/exploration/20/new.jpg";
        MerchantPlaceMediaUpload upload = MerchantPlaceMediaUpload.issue(
                PLACE_ID, USER_ID, s3Key, "image/jpeg", NOW.plusMinutes(10), NOW.minusMinutes(1)
        );
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(mediaUploadRepository.findByS3KeyForUpdate(s3Key)).thenReturn(Optional.of(upload));
        when(s3ObjectStorage.headObject(s3Key)).thenReturn(new S3ObjectStorage.S3ObjectMetadata(1_024L, "image/jpeg"));
        when(s3ObjectStorage.publicUrl(s3Key)).thenReturn("https://image");
        when(placeMediaRepository.findMaxDisplayOrder(PLACE_ID, PlaceMediaPurpose.EXPLORATION)).thenReturn(2);
        when(placeMediaRepository.save(any(PlaceMedia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlaceMediaItem response = service.createMedia(USER_ID, PLACE_ID, new MerchantOwnerMediaCreateRequest(s3Key, null));

        assertThat(response.imageUrl()).isEqualTo("https://image");
        assertThat(response.s3Key()).isEqualTo(s3Key);
        assertThat(response.displayOrder()).isEqualTo(3);
        assertThat(upload.getStatus()).isEqualTo(MerchantPlaceMediaUploadStatus.REGISTERED);
        assertThat(upload.getRegisteredAt()).isEqualTo(NOW);
    }

    /**
     * 만료 시각에 도달한 업로드 키는 잘못된 미디어 요청으로 거절하고 S3를 조회하지 않는지 검증.
     */
    @Test
    void rejectsExpiredMediaIssuance() {
        String s3Key = "places/10/exploration/20/expired.jpg";
        MerchantPlaceMediaUpload upload = MerchantPlaceMediaUpload.issue(
                PLACE_ID, USER_ID, s3Key, "image/jpeg", NOW, NOW.minusMinutes(10)
        );
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(mediaUploadRepository.findByS3KeyForUpdate(s3Key)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.createMedia(USER_ID, PLACE_ID, new MerchantOwnerMediaCreateRequest(s3Key, 0)))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));
        verifyNoInteractions(s3ObjectStorage);
    }

    /**
     * 다른 점주에게 발급된 키는 잘못된 미디어 요청으로 거절하고 S3를 호출하지 않는지 검증.
     */
    @Test
    void rejectsForeignMediaIssuance() {
        String s3Key = "places/10/exploration/21/other.jpg";
        MerchantPlaceMediaUpload upload = MerchantPlaceMediaUpload.issue(
                PLACE_ID, 21L, s3Key, "image/jpeg", NOW.plusMinutes(10), NOW.minusMinutes(1)
        );
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(mediaUploadRepository.findByS3KeyForUpdate(s3Key)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.createMedia(USER_ID, PLACE_ID, new MerchantOwnerMediaCreateRequest(s3Key, 0)))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));
        verifyNoInteractions(s3ObjectStorage);
    }

    /**
     * 마지막 미디어를 맨 앞으로 옮기면 세 항목의 순서가 0·1·2로 재배치되는지 검증.
     * 유일성 충돌을 피하기 위한 임시 순서 증가 호출도 확인.
     */
    @Test
    void movesAndNormalizesMediaOrder() {
        PlaceMedia first = explorationMedia(101L, 0);
        PlaceMedia second = explorationMedia(102L, 1);
        PlaceMedia third = explorationMedia(103L, 2);
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                PLACE_ID,
                PlaceMediaPurpose.EXPLORATION
        )).thenReturn(List.of(first, second, third));

        PlaceMediaItem response = service.updateMediaOrder(
                USER_ID,
                PLACE_ID,
                103L,
                new MerchantOwnerMediaOrderUpdateRequest(0)
        );

        assertThat(response.id()).isEqualTo(103L);
        assertThat(third.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(2);
        verify(placeMediaRepository).increaseDisplayOrder(PLACE_ID, PlaceMediaPurpose.EXPLORATION, 6);
    }

    /**
     * 미디어 한 개의 순서를 허용 범위 밖인 1로 바꾸면 잘못된 미디어 요청 오류를 반환하는지 검증.
     */
    @Test
    void rejectsOutOfRangeMediaOrder() {
        PlaceMedia media = explorationMedia(101L, 0);
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                PLACE_ID,
                PlaceMediaPurpose.EXPLORATION
        )).thenReturn(List.of(media));

        assertThatThrownBy(() -> service.updateMediaOrder(
                USER_ID,
                PLACE_ID,
                101L,
                new MerchantOwnerMediaOrderUpdateRequest(1)
        )).isInstanceOfSatisfying(MapException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));
    }

    /**
     * 해당 장소 목록에 없는 미디어 ID로 순서를 바꾸면 PLACE_MEDIA_NOT_FOUND를 반환하는지 검증.
     */
    @Test
    void rejectsMissingPlaceMedia() {
        when(mapPlaceRepository.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place()));
        when(placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                PLACE_ID,
                PlaceMediaPurpose.EXPLORATION
        )).thenReturn(List.of(explorationMedia(101L, 0)));

        assertThatThrownBy(() -> service.updateMediaOrder(
                USER_ID,
                PLACE_ID,
                999L,
                new MerchantOwnerMediaOrderUpdateRequest(0)
        )).isInstanceOfSatisfying(MapException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_MEDIA_NOT_FOUND));
    }

    /**
     * 순서 재배치 전후를 직접 비교할 수 있도록 식별자와 초기 순서를 가진 탐색용 미디어를 생성.
     */
    private PlaceMedia explorationMedia(Long id, int displayOrder) {
        PlaceMedia media = PlaceMedia.exploration(
                place(),
                "https://cdn.pingdom.test/media-%d.jpg".formatted(id),
                "places/10/exploration/20/media-%d.jpg".formatted(id),
                null,
                null,
                displayOrder,
                NOW
        );
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    /**
     * 업로드 경로 및 장소 잠금 조회의 기준이 되는 점주 소유 장소를 생성.
     */
    private MapPlace place() {
        return MapPlace.builder()
                .id(PLACE_ID)
                .name("테스트 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(USER_ID)
                .registrant("merchant")
                .build();
    }
}
