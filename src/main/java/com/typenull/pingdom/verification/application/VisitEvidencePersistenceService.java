package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitEvidencePersistenceService {
    private static final String UNIQUE_CHECK_IN_CONSTRAINT = "uq_visit_evidence_check_in";

    private final LocationCheckInRepository checkInRepository;
    private final VisitEvidenceRepository evidenceRepository;

    public VisitEvidencePersistenceService(LocationCheckInRepository checkInRepository,
            VisitEvidenceRepository evidenceRepository) {
        this.checkInRepository = checkInRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional
    public VisitEvidence save(Long userId, Long checkInId, String s3Key, String originalFilename,
            String contentType, long fileSize, Instant createdAt, Instant expiresAt) {
        requireOwnedCheckIn(userId, checkInId);
        try {
            return evidenceRepository.saveAndFlush(VisitEvidence.create(checkInId, userId, s3Key,
                    originalFilename, contentType, fileSize, createdAt, expiresAt));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, UNIQUE_CHECK_IN_CONSTRAINT)) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public VisitEvidence getOwned(Long userId, Long checkInId) {
        requireOwnedCheckIn(userId, checkInId);
        return evidenceRepository.findByLocationCheckInIdAndTouristUserId(checkInId, userId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.VISIT_EVIDENCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public void requireOwnedCheckIn(Long userId, Long checkInId) {
        checkInRepository.findByIdAndTouristUserId(checkInId, userId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND));
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) return true;
            current = current.getCause();
        }
        return false;
    }
}
