package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.*;
import com.typenull.pingdom.verification.api.dto.VisitEvidenceResponse;
import com.typenull.pingdom.verification.application.VisitEvidenceFileValidator.ValidatedVisitEvidenceFile;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.exception.*;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 체크인 소유권과 파일 검증을 거쳐 증빙 이미지의 S3 저장 및 DB 메타데이터 저장을 조율.
 * DB 트랜잭션은 별도 persistence 서비스가 담당하며 S3 작업은 원자성 보장 범위 외.
 * 저장 실패 시 업로드 객체의 삭제를 시도하지만, 정리 실패 복구는 제공 범위 외.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceService {
    private static final String S3_PREFIX = "visit-evidence";

    private final VisitEvidencePersistenceService persistenceService;
    private final VisitEvidenceFileValidator fileValidator;
    private final VisitEvidenceProperties properties;
    private final S3ObjectStorage objectStorage;
    private final Clock clock;

    /**
     * 본인 체크인에 검증·재인코딩한 이미지를 업로드하고 보관 기한을 포함한 메타데이터를 반환.
     * 소유권 또는 파일 검증 실패 시 S3 업로드를 시작하지 않으며, 저장 오류는 보상 처리 후 다시 전달.
     */
    public VisitEvidenceResponse upload(Long userId, Long checkInId, MultipartFile file) {
        persistenceService.requireOwnedCheckIn(userId, checkInId);
        ValidatedVisitEvidenceFile validated = fileValidator.validate(file);
        S3PutResult uploaded = upload(validated);
        Instant now = clock.instant();
        try {
            VisitEvidence saved = persistenceService.save(userId, checkInId, uploaded.key(),
                    validated.originalFilename(), validated.contentType(), validated.bytes().length,
                    now, now.plus(properties.retention()));
            return VisitEvidenceResponse.from(saved);
        } catch (RuntimeException exception) {
            // S3는 DB 트랜잭션에 참여하지 않으므로 이미 업로드한 객체를 별도로 정리.
            // 삭제도 실패할 수 있어 완전한 원자성을 보장하지 않으며, 최초 오류를 유지.
            cleanupUploadedObject(uploaded.key());
            throw exception;
        }
    }

    /** 본인 소유 체크인의 증빙 메타데이터를 조회하며, 소유권 또는 증빙 부재 오류는 그대로 전달. */
    public VisitEvidenceResponse get(Long userId, Long checkInId) {
        return VisitEvidenceResponse.from(persistenceService.getOwned(userId, checkInId));
    }

    /**
     * 소유권을 확인한 증빙의 바이트와 저장된 콘텐츠 타입을 반환.
     * S3 저장소 예외는 방문 인증의 저장소 사용 불가 오류로 변환하며 자동 재시도는 미지원.
     */
    public VisitEvidenceDownload download(Long userId, Long checkInId) {
        VisitEvidence evidence = persistenceService.getOwned(userId, checkInId);
        try {
            return new VisitEvidenceDownload(objectStorage.getBytes(evidence.getS3Key()), evidence.getContentType());
        } catch (S3StorageException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        }
    }

    /** 검증된 이미지에 서버 파일명을 사용해 업로드하고 S3 오류를 도메인 오류로 변환. */
    private S3PutResult upload(ValidatedVisitEvidenceFile file) {
        try {
            return objectStorage.put(file.bytes(), "evidence." + file.extension(), file.contentType(), S3_PREFIX);
        } catch (S3StorageException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        }
    }

    /**
     * 저장 실패로 남은 S3 객체의 삭제를 시도.
     * 삭제 중 발생한 RuntimeException은 key와 함께 기록하고 삼켜 호출자의 최초 실패를 보존.
     */
    private void cleanupUploadedObject(String key) {
        try {
            objectStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("증빙 DB 저장 실패 후 S3 객체 정리에 실패했습니다. key={}", key, exception);
        }
    }

    /** 다운로드 HTTP 응답에 사용할 이미지 바이트와 저장 시 확정한 MIME 타입. */
    public record VisitEvidenceDownload(byte[] content, String contentType) {}
}
