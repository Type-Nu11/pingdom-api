package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.MerchantPlaceAttachmentMalwareScanner;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceApplicationAttachmentServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long APPLICATION_ID = 20L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);

    @Mock private PlaceRegistrationApplicationRepository applicationRepository;
    @Mock private PlaceRegistrationAttachmentRepository attachmentRepository;
    @Mock private S3ObjectStorage storage;
    @Mock private S3ObjectDeleteOutboxPublisher deletePublisher;
    @Mock private MerchantPlaceAttachmentMalwareScanner malwareScanner;
    @Mock private PlaceRegistrationApplication application;

    private MerchantPlaceApplicationAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new MerchantPlaceApplicationAttachmentService(
                applicationRepository,
                attachmentRepository,
                storage,
                deletePublisher,
                malwareScanner,
                CLOCK
        );
        lenient().when(applicationRepository.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        lenient().when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        lenient().when(application.getApplicantUserId()).thenReturn(USER_ID);
        lenient().when(application.getStatus()).thenReturn(PlaceRegistrationStatus.DRAFT);
    }

    @Test
    void uploadCalculatesMetadataAndUsesPrivateServerGeneratedStorageKey() {
        MockMultipartFile file = jpeg("../../license.jpg");
        when(attachmentRepository.findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                APPLICATION_ID, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION)).thenReturn(List.of());
        when(storage.putPrivate(any(), eq("image/jpeg"),
                eq("private/merchant-place-applications/20/business_registration")))
                .thenReturn(new S3ObjectStorage.S3PutResult("private/generated-key", "ignored"));
        when(attachmentRepository.saveAndFlush(any(PlaceRegistrationAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(USER_ID, APPLICATION_ID, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, file);

        ArgumentCaptor<PlaceRegistrationAttachment> attachmentCaptor = ArgumentCaptor.forClass(PlaceRegistrationAttachment.class);
        verify(attachmentRepository).saveAndFlush(attachmentCaptor.capture());
        PlaceRegistrationAttachment attachment = attachmentCaptor.getValue();
        assertThat(attachment.getStorageKey()).isEqualTo("private/generated-key");
        assertThat(attachment.getFileHash()).hasSize(64);
        assertThat(attachment.getFileSize()).isEqualTo(3);
        assertThat(attachment.getOriginalFilename()).isEqualTo("license.jpg");
        assertThat(attachment.getRetentionExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 23, 0, 0));
        assertThat(response.toString()).doesNotContain("private/generated-key");
        verify(malwareScanner).scan(any());
    }

    @Test
    void uploadAlsoAllowsNewPlaceDraft() {
        lenient().when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        when(attachmentRepository.findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                APPLICATION_ID, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT)).thenReturn(List.of());
        when(storage.putPrivate(any(), eq("image/jpeg"), any()))
                .thenReturn(new S3ObjectStorage.S3PutResult("private/generated-key", "ignored"));
        when(attachmentRepository.saveAndFlush(any(PlaceRegistrationAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.upload(
                USER_ID, APPLICATION_ID, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, jpeg("id.jpg")
        )).doesNotThrowAnyException();

        verify(malwareScanner).scan(any());
        verify(storage).putPrivate(any(), eq("image/jpeg"), any());
    }

    @Test
    void uploadRejectsMimeSignatureMismatchBeforeScanOrStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, file))
                .isInstanceOf(PlaceRegistrationException.class)
                .extracting(exception -> ((PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);

        verify(malwareScanner, never()).scan(any());
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    @Test
    void uploadStopsBeforeStorageWhenMalwareScanFails() {
        MockMultipartFile file = jpeg("id.jpg");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("malware detected"))
                .when(malwareScanner).scan(any());

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, file))
                .isInstanceOf(IllegalArgumentException.class);

        verify(storage, never()).putPrivate(any(), any(), any());
    }

    @Test
    void uploadDeletesNewObjectWhenDatabasePersistenceFails() {
        MockMultipartFile file = jpeg("id.jpg");
        when(attachmentRepository.findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                APPLICATION_ID, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT)).thenReturn(List.of());
        when(storage.putPrivate(any(), eq("image/jpeg"), any()))
                .thenReturn(new S3ObjectStorage.S3PutResult("private/generated-key", "ignored"));
        when(attachmentRepository.saveAndFlush(any(PlaceRegistrationAttachment.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, file))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storage).delete("private/generated-key");
    }

    @Test
    void uploadRejectsOtherUsersBeforeReadingOrStoringFile() {
        when(application.getApplicantUserId()).thenReturn(99L);

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, jpeg("id.jpg")))
                .isInstanceOf(PlaceRegistrationException.class)
                .extracting(exception -> ((PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(PlaceRegistrationErrorCode.ACCESS_DENIED);

        verify(malwareScanner, never()).scan(any());
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    private MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }
}
