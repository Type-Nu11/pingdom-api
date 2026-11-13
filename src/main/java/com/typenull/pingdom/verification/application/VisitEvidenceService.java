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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 체크인 소유권과 파일 검증을 거쳐 증빙 이미지의 S3 저장 및 DB 메타데이터 저장을 조율.
 * DB 트랜잭션은 별도 persistence 서비스가 담당하며 S3 작업은 원자성 보장 범위 외.
 * 소유 key는 업로드 전 기록하며 실패한 미참조 객체는 14일 유예 후 별도 정리.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitEvidenceService {
    private final VisitEvidencePersistenceService persistenceService;
    private final VisitEvidenceFileValidator fileValidator;
    private final VisitEvidenceProperties properties;
    private final S3ObjectStorage objectStorage;
    private final Clock clock;
    private final VisitEvidenceUploadIntentService intents;
    private final VisitEvidenceOrphanProperties orphanProperties;

    /**
     * 본인 체크인에 검증·재인코딩한 이미지를 업로드하고 보관 기한을 포함한 메타데이터를 반환.
     * 소유권 또는 파일 검증 실패 시 S3 업로드를 시작하지 않으며, 저장 오류는 orphan 추적 상태를 기록한 후 다시 전달.
     */
    @Transactional(propagation = Propagation.NEVER)
    public VisitEvidenceResponse upload(Long userId, Long checkInId, MultipartFile file) {
        persistenceService.requireOwnedCheckIn(userId, checkInId);
        ValidatedVisitEvidenceFile validated = fileValidator.validate(file);
        var upload = intents.prepare(userId, checkInId, validated.extension());
        try {
            objectStorage.putAtKey(validated.bytes(), validated.contentType(), upload.key(), orphanProperties.uploadTimeout());
            Instant now = clock.instant();
            VisitEvidence saved = intents.complete(upload.intentId(), userId, checkInId,
                    validated.originalFilename(), validated.contentType(), validated.bytes().length,
                    now, now.plus(properties.retention()));
            return VisitEvidenceResponse.from(saved);
        } catch (RuntimeException exception) {
            try { intents.abandon(upload.intentId()); }
            catch (RuntimeException cleanupFailure) {
                // DB 장애 중에도 선등록 key는 남으며 deadline 경과 후 다시 판정합니다.
                log.warn("증빙 업로드 실패 상태 기록에 실패했습니다. intentId={}", upload.intentId(), cleanupFailure);
            }
            if (exception instanceof S3StorageException)
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
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

    /** 다운로드 HTTP 응답에 사용할 이미지 바이트와 저장 시 확정한 MIME 타입. */
    public record VisitEvidenceDownload(byte[] content, String contentType) {}
}
