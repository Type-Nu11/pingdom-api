package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitEvidenceUploadIntent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitEvidenceUploadIntentRepository extends JpaRepository<VisitEvidenceUploadIntent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from VisitEvidenceUploadIntent i where i.id = :id")
    Optional<VisitEvidenceUploadIntent> findForUpdate(@Param("id") String id);

    @Query(value = """
            SELECT * FROM visit_evidence_upload_intent
            WHERE next_check_at <= :now
            ORDER BY next_check_at, id LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<VisitEvidenceUploadIntent> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
