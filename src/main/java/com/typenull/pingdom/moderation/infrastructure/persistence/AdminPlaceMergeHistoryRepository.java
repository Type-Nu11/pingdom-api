package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.place.AdminPlaceMergeHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminPlaceMergeHistoryRepository extends JpaRepository<AdminPlaceMergeHistory, Long> {

    List<AdminPlaceMergeHistory> findTop50ByOrderByMergedAtDescIdDesc();
}
