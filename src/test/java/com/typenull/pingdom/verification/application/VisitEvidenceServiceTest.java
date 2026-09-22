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
    private final VisitEvidencePersistenceService persistenceService = mock(VisitEvidencePersistenceService.class);
    private final S3ObjectStorage objectStorage = mock(S3ObjectStorage.class);
    private VisitEvidenceService service;

    /**
     * 시계를 고정하고 보관 기간 30일·최대 파일 크기 1KB로 서비스를 구성한다.
     * S3는 고정 key를 반환하고 저장 mock은 전달받은 메타데이터로 증빙을 만들어,
     * 실제 외부 저장소 없이 업로드와 저장에 전달되는 값을 확인할 수 있게 한다.
     */
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

    /**
     * JPEG 업로드 결과가 체크인 2와 연결되고 고정 시각으로부터 30일 후 만료되는지 검증한다.
     * S3가 반환한 key와 JPEG 콘텐츠 타입, 생성·만료 시각이 저장 서비스에 전달되어야 한다.
     */
    @Test
    void uploadWithRetention() throws Exception {
        var response = service.upload(1L, 2L, jpeg());

        assertThat(response.locationCheckInId()).isEqualTo(2L);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        verify(persistenceService).save(eq(1L), eq(2L), eq("visit-evidence/key"), anyString(),
                eq("image/jpeg"), anyLong(), eq(NOW), eq(NOW.plus(Duration.ofDays(30))));
    }

    /**
     * 소유권 확인이 CHECK_IN_NOT_FOUND로 실패하면 같은 오류를 호출자에게 전달한다.
     * S3와 어떤 상호작용도 발생하지 않아 권한 확인 전에 파일이 업로드되는 회귀를 방지한다.
     */
    @Test
    void rejectUnownedCheckIn() throws Exception {
        doThrow(new VisitorVerificationException(VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND))
                .when(persistenceService).requireOwnedCheckIn(1L, 3L);

        assertError(() -> service.upload(1L, 3L, jpeg()), VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND);
        verifyNoInteractions(objectStorage);
    }

    /**
     * S3 업로드 후 저장이 중복 증빙 오류로 실패하면 업로드한 key를 삭제 요청하는지 검증한다.
     * 보상 처리를 하더라도 호출자에게는 VISIT_EVIDENCE_ALREADY_EXISTS를 전달해야 한다.
     */
    @Test
    void cleanUpFailedSave() throws Exception {
        when(persistenceService.save(eq(1L), eq(2L), anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS));

        assertError(() -> service.upload(1L, 2L, jpeg()),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
        verify(objectStorage).delete("visit-evidence/key");
    }

    /**
     * 중복 증빙 저장 실패에 이어 S3 삭제까지 연결 오류로 실패하는 상황을 구성한다.
     * 삭제 시도는 수행하되 정리 오류가 최초 중복 증빙 오류를 덮어쓰지 않아야 한다.
     */
    @Test
    void preserveOriginalSaveError() throws Exception {
        when(persistenceService.save(eq(1L), eq(2L), anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenThrow(new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS));
        doThrow(new S3StorageException(S3StorageError.CONNECTION_ERROR, "cleanup failure", null))
                .when(objectStorage).delete("visit-evidence/key");

        assertError(() -> service.upload(1L, 2L, jpeg()),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
        verify(objectStorage).delete("visit-evidence/key");
    }

    /**
     * 첫 S3 업로드의 연결 오류는 저장소 사용 불가 오류로 변환되고 DB 저장은 호출되지 않는다.
     * 호출자가 다시 업로드하면 새 S3 key로 증빙이 저장되어야 한다.
     * 서비스 내부의 자동 재시도가 아니라 두 번의 명시적 호출을 검증한다.
     */
    @Test
    void retryFailedUpload() throws Exception {
        when(objectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("visit-evidence")))
                .thenThrow(new S3StorageException(S3StorageError.CONNECTION_ERROR, "temporary failure", null))
                .thenReturn(new S3ObjectStorage.S3PutResult("visit-evidence/retry-key", "unused"));

        assertError(() -> service.upload(1L, 2L, jpeg()),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        verify(persistenceService, never()).save(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                anyLong(), any(), any());

        var retried = service.upload(1L, 2L, jpeg());

        assertThat(retried.locationCheckInId()).isEqualTo(2L);
        verify(persistenceService).save(eq(1L), eq(2L), eq("visit-evidence/retry-key"), anyString(),
                eq("image/jpeg"), anyLong(), eq(NOW), eq(NOW.plus(Duration.ofDays(30))));
    }

    /** 이미지 디코딩 검사를 통과하는 2×2 RGB JPEG를 메모리에서 만들어 업로드 입력으로 사용한다. */
    private MockMultipartFile jpeg() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", output);
        return new MockMultipartFile("file", "visit.jpg", "image/jpeg", output.toByteArray());
    }

    /** 실행 결과가 방문 인증 예외인지 확인하고 내부 오류 코드까지 기대값과 대조한다. */
    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            VisitorVerificationErrorCode expected) {
        assertThatThrownBy(callable).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
