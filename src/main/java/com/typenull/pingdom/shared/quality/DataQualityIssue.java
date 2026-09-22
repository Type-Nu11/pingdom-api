package com.typenull.pingdom.shared.quality;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 데이터 품질 규칙 위반의 대상·심각도·발견 시각을 기록한다. 현재 생성 경로는 OPEN이며 해결 전 resolvedAt은 null이다. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "data_quality_issue")
public class DataQualityIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_quality_issue_id") private Long id;
    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id", nullable = false) private Long entityId;
    @Column(name = "rule_code", nullable = false, length = 80) private String ruleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DataQualityIssueSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DataQualityIssueStatus status;
    @Column(length = 500) private String details;
    @Column(name = "detected_at", nullable = false) private LocalDateTime detectedAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    /** 대상 식별자와 필수 규칙 정보를 검증해 OPEN 이슈를 만든다. 동일 대상·규칙의 중복 탐지는 수행하지 않는다. */
    public static DataQualityIssue open(String entityType, long entityId, String ruleCode,
                                        DataQualityIssueSeverity severity, String details, LocalDateTime detectedAt) {
        if (entityType == null || entityType.isBlank() || entityId <= 0 || ruleCode == null || ruleCode.isBlank()
                || severity == null || detectedAt == null) throw new IllegalArgumentException("invalid quality issue");
        var issue = new DataQualityIssue(); issue.entityType = entityType.trim(); issue.entityId = entityId;
        issue.ruleCode = ruleCode.trim(); issue.severity = severity; issue.status = DataQualityIssueStatus.OPEN;
        issue.details = details; issue.detectedAt = detectedAt; return issue;
    }
}
