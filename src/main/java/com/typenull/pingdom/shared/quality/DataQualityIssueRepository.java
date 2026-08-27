package com.typenull.pingdom.shared.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, Long> {
    Page<DataQualityIssue> findAllByStatus(DataQualityIssueStatus status, Pageable pageable);
}
