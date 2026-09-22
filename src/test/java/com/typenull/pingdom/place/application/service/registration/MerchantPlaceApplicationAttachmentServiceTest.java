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

    /**
     * 본인 소유의 Claim 초안과 고정 보존 기준 시각을 준비합니다.
     */
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

    /**
     * 서버가 받은 객체 키와 계산한 해시·크기, 경로를 제거한 파일명·30일 기한을 저장하고 응답에서 private 키를 감추는지 확인합니다.
     */
    @Test
    void storesValidatedPrivateAttachment() {
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

    /**
     * NEW_PLACE 초안도 악성 파일 검사와 private 업로드를 통과할 수 있는지 확인합니다.
     */
    @Test
    void allowsNewPlaceAttachment() {
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

    /**
     * JPEG로 신고한 PDF 시그니처를 메타데이터 오류로 거절하고 스캔·저장 전에 중단하는지 확인합니다.
     */
    @Test
    void rejectsMimeSignatureMismatch() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, file))
                .isInstanceOf(PlaceRegistrationException.class)
                .extracting(exception -> ((PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);

        verify(malwareScanner, never()).scan(any());
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    /**
     * 악성 파일 검사 예외를 전파하면서 객체 저장을 실행하지 않는지 확인합니다.
     */
    @Test
    void stopsAfterMalwareFailure() {
        MockMultipartFile file = jpeg("id.jpg");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("malware detected"))
                .when(malwareScanner).scan(any());

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, file))
                .isInstanceOf(IllegalArgumentException.class);

        verify(storage, never()).putPrivate(any(), any(), any());
    }

    /**
     * 첨부 DB 저장 실패 시 원래 무결성 예외를 유지하면서 새 S3 객체 보상 삭제를 호출하는지 확인합니다.
     */
    @Test
    void cleansUploadAfterPersistenceFailure() {
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

    /**
     * 다른 신청자의 첨부 업로드를 접근 거절로 처리하고 스캔·저장을 수행하지 않는지 확인합니다.
     */
    @Test
    void rejectsOtherApplicantUpload() {
        when(application.getApplicantUserId()).thenReturn(99L);

        assertThatThrownBy(() -> service.upload(USER_ID, APPLICATION_ID,
                PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, jpeg("id.jpg")))
                .isInstanceOf(PlaceRegistrationException.class)
                .extracting(exception -> ((PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(PlaceRegistrationErrorCode.ACCESS_DENIED);

        verify(malwareScanner, never()).scan(any());
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    /**
     * 시그니처 검사에 필요한 최소 JPEG 바이트와 지정 파일명을 가진 입력을 만듭니다.
     */
    private MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }
}
