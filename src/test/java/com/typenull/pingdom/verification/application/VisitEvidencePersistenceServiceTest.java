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

    @BeforeEach
    void setUp() {
        when(checkInRepository.findByIdAndTouristUserId(2L, 1L)).thenReturn(Optional.of(mock(LocationCheckIn.class)));
    }

    @Test
    void mapsOnlyCheckInUniqueConstraintToConflict() {
        when(evidenceRepository.saveAndFlush(any())).thenThrow(integrityViolation("uq_visit_evidence_check_in"));

        assertThatThrownBy(this::save).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS);
    }

    @Test
    void propagatesUnrelatedIntegrityViolation() {
        DataIntegrityViolationException failure = integrityViolation("fk_visit_evidence_check_in");
        when(evidenceRepository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(this::save).isSameAs(failure);
    }

    private void save() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        service.save(1L, 2L, "key", "visit.jpg", "image/jpeg", 4, now, now.plusSeconds(60));
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException("constraint",
                new ConstraintViolationException("constraint", new SQLException(), constraintName));
    }
}
