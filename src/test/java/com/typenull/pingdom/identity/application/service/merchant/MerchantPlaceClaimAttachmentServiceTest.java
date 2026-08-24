package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimAttachmentRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.shared.support.MerchantPlaceAttachmentMalwareScanner;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceClaimAttachmentServiceTest {
    private static final Long USER_ID = 10L;
    private static final Long CLAIM_ID = 20L;
    private static final Long ATTACHMENT_ID = 30L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);

    @Mock private MerchantPlaceClaimRepository claimRepository;
    @Mock private MerchantPlaceClaimAttachmentRepository attachmentRepository;
    @Mock private S3ObjectStorage storage;
    @Mock private S3ObjectDeleteOutboxPublisher deletePublisher;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private MerchantPlaceAttachmentMalwareScanner malwareScanner;
    private MerchantPlaceClaimAttachmentService service;
    private MerchantPlaceClaim claim;

    @BeforeEach
    void setUp() {
        service = new MerchantPlaceClaimAttachmentService(
                claimRepository, attachmentRepository, storage, deletePublisher, CLOCK, auditLogService, malwareScanner);
        claim = MerchantPlaceClaim.pending(USER_ID, 99L, null, "reason", LocalDateTime.now(CLOCK));
        lenient().when(claimRepository.findByIdAndMerchantOwnerUserId(CLAIM_ID, USER_ID))
                .thenReturn(Optional.of(claim));
    }

    @Test
    void uploadStoresValidatedFileWithoutExposingStorageKey() {
        MockMultipartFile file = file("license.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(attachmentRepository.findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.BUSINESS_LICENSE)).thenReturn(List.of());
        when(storage.putPrivate(any(), eq("image/jpeg"), eq("private/merchant-place-claims/20/business_license")))
                .thenReturn(new S3ObjectStorage.S3PutResult("private/key", "https://public-url.example/key"));
        when(attachmentRepository.save(any(MerchantPlaceClaimAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(USER_ID, CLAIM_ID, MerchantPlaceClaimAttachmentType.BUSINESS_LICENSE, file);

        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.fileSize()).isEqualTo(3);
        verify(storage).putPrivate(any(), eq("image/jpeg"), any());
        assertThat(response.toString()).doesNotContain("private/key");
    }

    @Test
    void uploadRejectsMimeSignatureMismatchBeforeStorage() {
        MockMultipartFile file = file("fake.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, CLAIM_ID,
                MerchantPlaceClaimAttachmentType.RESIDENT_REGISTRATION, file))
                .isInstanceOf(MerchantOwnerException.class)
                .hasMessageContaining("지원하지 않거나 손상된");
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    @Test
    void replacingSensitiveAttachmentDeletesPreviousObjectThroughOutbox() {
        MerchantPlaceClaimAttachment old = MerchantPlaceClaimAttachment.create(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.RESIDENT_REGISTRATION, "private/old",
                "old.jpg", "image/jpeg", 3, "old-hash", 0, LocalDateTime.now(CLOCK));
        MockMultipartFile file = file("new.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(attachmentRepository.findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.RESIDENT_REGISTRATION)).thenReturn(List.of(old));
        when(storage.putPrivate(any(), eq("image/jpeg"), any()))
                .thenReturn(new S3ObjectStorage.S3PutResult("private/new", "ignored"));
        when(attachmentRepository.save(any(MerchantPlaceClaimAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(USER_ID, CLAIM_ID, MerchantPlaceClaimAttachmentType.RESIDENT_REGISTRATION, file);

        verify(attachmentRepository).delete(old);
        verify(deletePublisher).publish("private/old", "MERCHANT_PLACE_CLAIM_ATTACHMENT", "20", "REPLACED");
    }

    @Test
    void uploadRejectsOversizedFileBeforeMalwareScanOrStorage() {
        MockMultipartFile file = file("large.jpg", "image/jpeg", new byte[20 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(USER_ID, CLAIM_ID,
                MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, file))
                .isInstanceOf(MerchantOwnerException.class)
                .hasMessageContaining("크기");
        verify(malwareScanner, never()).scan(any());
        verify(storage, never()).putPrivate(any(), any(), any());
    }

    @Test
    void uploadRejectsSameFileHashWithoutReplacingExistingAttachment() {
        byte[] bytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        MerchantPlaceClaimAttachment existing = MerchantPlaceClaimAttachment.create(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, "private/existing",
                "existing.jpg", "image/jpeg", bytes.length,
                "6e568e1f67fba258184c78181539e5e8fdee447e49bb706fc0ea34fbf12336a5", 0,
                LocalDateTime.now(CLOCK));
        when(attachmentRepository.findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE)).thenReturn(List.of(existing));

        MockMultipartFile file = file("same.jpg", "image/jpeg", bytes);

        assertThatThrownBy(() -> service.upload(USER_ID, CLAIM_ID,
                MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, file))
                .isInstanceOf(MerchantOwnerException.class)
                .hasMessageContaining("동일한");
        verify(storage, never()).putPrivate(any(), any(), any());
        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void reorderRejectsUnknownOrIncompleteRepresentativeImageIds() {
        MerchantPlaceClaimAttachment first = MerchantPlaceClaimAttachment.create(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, "private/first",
                "first.jpg", "image/jpeg", 3, "hash-1", 0, LocalDateTime.now(CLOCK));
        MerchantPlaceClaimAttachment second = MerchantPlaceClaimAttachment.create(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, "private/second",
                "second.jpg", "image/jpeg", 3, "hash-2", 1, LocalDateTime.now(CLOCK));
        when(attachmentRepository.findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorder(USER_ID, CLAIM_ID, List.of(999L, 1000L)))
                .isInstanceOf(MerchantOwnerException.class)
                .hasMessageContaining("첨부");
    }

    @Test
    void deleteRequiresClaimOwnership() {
        when(claimRepository.findByIdAndMerchantOwnerUserId(CLAIM_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, CLAIM_ID, ATTACHMENT_ID))
                .isInstanceOf(MerchantOwnerException.class)
                .hasMessageContaining("장소 Claim 요청");
        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void adminDownloadReturnsBytesAndRecordsAuditWithoutReturningKey() {
        MerchantPlaceClaimAttachment attachment = MerchantPlaceClaimAttachment.create(
                CLAIM_ID, MerchantPlaceClaimAttachmentType.RESIDENT_REGISTRATION, "private/secret",
                "id.jpg", "image/jpeg", 3, "hash", 0, LocalDateTime.now(CLOCK));
        when(attachmentRepository.findByIdAndClaimId(ATTACHMENT_ID, CLAIM_ID)).thenReturn(Optional.of(attachment));
        when(storage.getBytes("private/secret")).thenReturn(new byte[] {1, 2, 3});

        var downloaded = service.downloadForAdmin(99L, CLAIM_ID, ATTACHMENT_ID);

        assertThat(downloaded.bytes()).containsExactly(1, 2, 3);
        assertThat(downloaded.contentType()).isEqualTo("image/jpeg");
        verify(auditLogService).record(eq(99L), any(), any(), eq(CLAIM_ID), any(), eq(null), eq(null));
    }

    private MockMultipartFile file(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }
}
