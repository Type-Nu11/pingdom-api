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
            cleanupUploadedObject(uploaded.key());
            throw exception;
        }
    }

    public VisitEvidenceResponse get(Long userId, Long checkInId) {
        return VisitEvidenceResponse.from(persistenceService.getOwned(userId, checkInId));
    }

    public VisitEvidenceDownload download(Long userId, Long checkInId) {
        VisitEvidence evidence = persistenceService.getOwned(userId, checkInId);
        try {
            return new VisitEvidenceDownload(objectStorage.getBytes(evidence.getS3Key()), evidence.getContentType());
        } catch (S3StorageException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        }
    }

    private S3PutResult upload(ValidatedVisitEvidenceFile file) {
        try {
            return objectStorage.put(file.bytes(), "evidence." + file.extension(), file.contentType(), S3_PREFIX);
        } catch (S3StorageException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_STORAGE_UNAVAILABLE);
        }
    }

    private void cleanupUploadedObject(String key) {
        try {
            objectStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("증빙 DB 저장 실패 후 S3 객체 정리에 실패했습니다. key={}", key, exception);
        }
    }

    public record VisitEvidenceDownload(byte[] content, String contentType) {}
}
