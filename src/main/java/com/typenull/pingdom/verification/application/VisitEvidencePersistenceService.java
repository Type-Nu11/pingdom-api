package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체크인 소유권 확인과 증빙 메타데이터의 DB 저장·조회를 담당한다.
 * 업로드 조율 서비스에서 별도 Bean으로 호출하여 DB 트랜잭션을 적용하며 S3 작업은 담당하지 않는다.
 */
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

    /**
     * 저장 트랜잭션 안에서 소유권을 다시 확인하고 증빙을 즉시 flush한다.
     * 지정한 체크인 유일 제약 위반만 이미 등록된 증빙 오류로 변환하며 다른 무결성 오류는 유지한다.
     * 저장 실패는 호출한 업로드 서비스로 전달되어 S3 객체 정리의 계기가 된다.
     */
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

    /** 본인 체크인 존재 여부를 먼저 확인한 뒤 체크인과 사용자 두 조건으로 증빙을 조회한다. */
    @Transactional(readOnly = true)
    public VisitEvidence getOwned(Long userId, Long checkInId) {
        requireOwnedCheckIn(userId, checkInId);
        return evidenceRepository.findByLocationCheckInIdAndTouristUserId(checkInId, userId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.VISIT_EVIDENCE_NOT_FOUND));
    }

    /**
     * 체크인 ID와 사용자 ID가 함께 일치해야 하며, 없거나 타인 소유이면 동일한 부재 오류를 낸다.
     * 같은 Bean의 save/getOwned에서 호출하면 새 트랜잭션을 열지 않고 호출 메서드의 범위에서 실행된다.
     */
    @Transactional(readOnly = true)
    public void requireOwnedCheckIn(Long userId, Long checkInId) {
        checkInRepository.findByIdAndTouristUserId(checkInId, userId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.CHECK_IN_NOT_FOUND));
    }

    /** 원인 예외 체인에서 Hibernate가 보고한 제약 이름을 대소문자 구분 없이 비교한다. */
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
