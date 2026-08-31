package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void createUploadUrlRecordsTheIssuanceScope() {
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

    @Test
    void createMediaRegistersOnlyTheIssuedUploadedObject() {
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

    @Test
    void createMediaRejectsExpiredIssuanceBeforeReadingS3() {
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

    @Test
    void createMediaRejectsObjectIssuedForAnotherMerchant() {
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
