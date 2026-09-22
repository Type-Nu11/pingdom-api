package com.typenull.pingdom.shared.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 품질 이슈 저장과 상태별 페이지 조회 경계다. 정렬과 페이지 상한은 호출 서비스가 제공한다. */
public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, Long> {
    Page<DataQualityIssue> findAllByStatus(DataQualityIssueStatus status, Pageable pageable);
}
