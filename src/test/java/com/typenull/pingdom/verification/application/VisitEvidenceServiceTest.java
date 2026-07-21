package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.shared.support.S3ObjectStorage;
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
    private final VisitEvidencePersistenceService persistenceService = mock(VisitEvidencePersistenceService.class);
    private final S3ObjectStorage objectStorage = mock(S3ObjectStorage.class);
    private VisitEvidenceService service;

    @BeforeEach
    void setUp() {
        VisitEvidenceProperties properties = new VisitEvidenceProperties(Duration.ofDays(30), 1024L, 10, 10);
        service = new VisitEvidenceService(persistenceService, new VisitEvidenceFileValidator(properties), properties,
                objectStorage, Clock.fixed(NOW, ZoneOffset.UTC));
        when(objectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("visit-evidence")))
                .thenReturn(new S3ObjectStorage.S3PutResult("visit-evidence/key", "unused"));
        when(persistenceService.save(eq(1L), eq(2L), anyString(), anyString(), eq("image/jpeg"),
                anyLong(), eq(NOW), eq(NOW.plus(Duration.ofDays(30)))))
                .thenAnswer(invocation -> VisitEvidence.create(2L, 1L, invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5),
                        invocation.getArgument(6), invocation.getArgument(7)));
    }

    @Test
    void uploadsEvidenceForOwnedCheckInWithRetentionDate() throws Exception {
        var response = service.upload(1L, 2L, jpeg());

        assertThat(response.locationCheckInId()).isEqualTo(2L);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        verify(persistenceService).save(eq(1L), eq(2L), eq("visit-evidence/key"), anyString(),
                eq("image/jpeg"), anyLong(), eq(NOW), eq(NOW.plus(Duration.ofDays(30))));
    }

    @Test
    void rejectsCheckInOwnedByAnotherUserBeforeUpload() throws Exception {
        doThrow(new VisitorVerificationException(VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND))
                .when(persistenceService).requireOwnedCheckIn(1L, 3L);

        assertError(() -> service.upload(1L, 3L, jpeg()), VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void deletesUploadedObjectWhenDatabaseSaveFails() throws Exception {
        when(persistenceService.save(eq(1L), eq(2L), anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS));

        assertError(() -> service.upload(1L, 2L, jpeg()),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
        verify(objectStorage).delete("visit-evidence/key");
    }

    private MockMultipartFile jpeg() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output);
        return new MockMultipartFile("file", "visit.jpg", "image/jpeg", output.toByteArray());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            VisitorVerificationErrorCode expected) {
        assertThatThrownBy(callable).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
