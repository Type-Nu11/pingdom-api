package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.place.AdminPlaceMergeHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminPlaceMergeHistoryRepository extends JpaRepository<AdminPlaceMergeHistory, Long> {

    Page<AdminPlaceMergeHistory> findAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AdminPlaceMergeHistory h WHERE h.id = :historyId")
    Optional<AdminPlaceMergeHistory> findByIdForUpdate(@Param("historyId") Long historyId);
}
