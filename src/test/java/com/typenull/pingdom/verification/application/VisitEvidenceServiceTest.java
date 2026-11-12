package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.exception.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VisitEvidenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private static final String ID = "8a7e3f69-b920-4aad-8eb8-8862dfd6e152";
    private static final String KEY = "visit-evidence/tracked/" + ID + ".jpg";
    private final VisitEvidencePersistenceService persistence = mock(VisitEvidencePersistenceService.class);
    private final VisitEvidenceUploadIntentService intents = mock(VisitEvidenceUploadIntentService.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final VisitEvidenceOrphanProperties orphan = new VisitEvidenceOrphanProperties(null, null, null);
    private VisitEvidenceService service;

    @BeforeEach
    void setUp() {
        VisitEvidenceProperties properties = new VisitEvidenceProperties(Duration.ofDays(30), 1024L, 10, 10);
        service = new VisitEvidenceService(persistence, new VisitEvidenceFileValidator(properties), properties,
                storage, Clock.fixed(NOW, ZoneOffset.UTC), intents, orphan);
    }

    @Test
    void registerKeyBeforeUploadAndPreserveActiveRetention() throws Exception {
        prepare();
        VisitEvidence saved = VisitEvidence.create(2L, 1L, KEY, "visit.jpg", "image/jpeg", 4,
                NOW, NOW.plus(Duration.ofDays(30)));
        when(intents.complete(eq(ID), eq(1L), eq(2L), anyString(), eq("image/jpeg"), anyLong(),
                eq(NOW), eq(NOW.plus(Duration.ofDays(30))))).thenReturn(saved);

        var response = service.upload(1L, 2L, jpeg());

        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        var ordered = inOrder(intents, storage);
        ordered.verify(intents).prepare(1L, 2L, "jpg");
        ordered.verify(storage).putAtKey(any(byte[].class), eq("image/jpeg"), eq(KEY), eq(orphan.uploadTimeout()));
        ordered.verify(intents).complete(eq(ID), eq(1L), eq(2L), anyString(), eq("image/jpeg"), anyLong(), any(), any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectUnownedCheckInBeforeCreatingIntent() throws Exception {
        doThrow(new VisitorVerificationException(VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND))
                .when(persistence).requireOwnedCheckIn(1L, 3L);
        assertError(() -> service.upload(1L, 3L, jpeg()), VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND);
        verifyNoInteractions(storage, intents);
    }

    @Test
    void failedSaveRecordsOrphanWithoutImmediateDelete() throws Exception {
        prepare();
        when(intents.complete(eq(ID), eq(1L), eq(2L), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS));
        assertError(() -> service.upload(1L, 2L, jpeg()), VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
        verify(intents).abandon(ID);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void preserveOriginalSaveErrorWhenAbandonFails() throws Exception {
        prepare();
        when(intents.complete(eq(ID), eq(1L), eq(2L), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS));
        doThrow(new IllegalStateException("database unavailable")).when(intents).abandon(ID);
        assertError(() -> service.upload(1L, 2L, jpeg()), VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void failedUploadLeavesTrackedKeyAndDoesNotComplete() throws Exception {
        prepare();
        when(storage.putAtKey(any(byte[].class), anyString(), eq(KEY), eq(orphan.uploadTimeout())))
                .thenThrow(new S3StorageException(S3StorageError.CONNECTION_ERROR, "upload failed", null));
        assertError(() -> service.upload(1L, 2L, jpeg()), VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        verify(intents).abandon(ID);
        verify(intents, never()).complete(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyLong(), any(), any());
    }

    @Test
    void retryUsesIndependentTrackedIntent() throws Exception {
        String retryId = "d4c03bdb-24bf-4657-abda-98db6648434e";
        String retryKey = "visit-evidence/tracked/" + retryId + ".jpg";
        when(intents.prepare(1L, 2L, "jpg"))
                .thenReturn(new VisitEvidenceUploadIntentService.PreparedUpload(ID, KEY),
                        new VisitEvidenceUploadIntentService.PreparedUpload(retryId, retryKey));
        when(storage.putAtKey(any(byte[].class), anyString(), eq(KEY), eq(orphan.uploadTimeout())))
                .thenThrow(new S3StorageException(S3StorageError.CONNECTION_ERROR, "late put possible", null));
        var saved = VisitEvidence.create(2L, 1L, retryKey, "visit.jpg", "image/jpeg", 4,
                NOW, NOW.plus(Duration.ofDays(30)));
        when(intents.complete(eq(retryId), eq(1L), eq(2L), anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(saved);

        assertError(() -> service.upload(1L, 2L, jpeg()), VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        assertThat(service.upload(1L, 2L, jpeg()).locationCheckInId()).isEqualTo(2L);
        verify(intents).abandon(ID);
        verify(storage).putAtKey(any(byte[].class), eq("image/jpeg"), eq(retryKey), eq(orphan.uploadTimeout()));
        verify(storage, never()).delete(anyString());
    }

    private void prepare() {
        when(intents.prepare(1L, 2L, "jpg")).thenReturn(new VisitEvidenceUploadIntentService.PreparedUpload(ID, KEY));
    }

    private MockMultipartFile jpeg() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        return new MockMultipartFile("file", "visit.jpg", "image/jpeg", bytes.toByteArray());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            VisitorVerificationErrorCode expected) {
        assertThatThrownBy(callable).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode()).isEqualTo(expected);
    }
}
