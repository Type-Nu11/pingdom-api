package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class VisitEvidencePersistenceServiceTest {
    private final LocationCheckInRepository checkInRepository = mock(LocationCheckInRepository.class);
    private final VisitEvidenceRepository evidenceRepository = mock(VisitEvidenceRepository.class);
    private final VisitEvidencePersistenceService service =
            new VisitEvidencePersistenceService(checkInRepository, evidenceRepository);

    /** 사용자 1이 체크인 2를 소유한 것으로 조회 mock을 구성해 저장 제약 처리에 집중한다. */
    @BeforeEach
    void setUp() {
        when(checkInRepository.findByIdAndTouristUserId(2L, 1L)).thenReturn(Optional.of(mock(LocationCheckIn.class)));
    }

    /**
     * 체크인 증빙 유일 제약 위반을 중첩 Hibernate 예외로 구성한다.
     * 저장 시 VISIT_EVIDENCE_ALREADY_EXISTS로 변환되어야 한다.
     */
    @Test
    void mapDuplicateEvidence() {
        when(evidenceRepository.saveAndFlush(any())).thenThrow(integrityViolation("uq_visit_evidence_check_in"));

        assertThatThrownBy(this::save).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
    }

    /**
     * 외래 키 제약 위반은 중복 증빙 오류로 오인하지 않아야 한다.
     * 처음 발생한 DataIntegrityViolationException 인스턴스가 그대로 전달되는지 확인한다.
     */
    @Test
    void preserveOtherIntegrityError() {
        DataIntegrityViolationException failure = integrityViolation("fk_visit_evidence_check_in");
        when(evidenceRepository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(this::save).isSameAs(failure);
    }

    /** 유효한 크기와 생성 시각보다 60초 뒤인 만료 시각으로 증빙 저장을 호출한다. */
    private void save() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        service.save(1L, 2L, "key", "visit.jpg", "image/jpeg", 4, now, now.plusSeconds(60));
    }

    /** 지정한 제약 이름이 원인 체인에 포함되도록 Spring·Hibernate 무결성 예외를 중첩한다. */
    private DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("constraint",
                new ConstraintViolationException("constraint", new SQLException(), constraintName));
    }
}
