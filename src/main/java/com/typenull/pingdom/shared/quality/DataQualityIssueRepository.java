package com.typenull.pingdom.shared.quality;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, Long> {
    List<DataQualityIssue> findTop100ByStatusOrderByDetectedAtDesc(DataQualityIssueStatus status);
}
