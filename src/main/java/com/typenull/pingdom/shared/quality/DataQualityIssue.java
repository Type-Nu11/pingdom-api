package com.typenull.pingdom.shared.quality;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public static DataQualityIssue open(String entityType, long entityId, String ruleCode,
                                        DataQualityIssueSeverity severity, String details, LocalDateTime detectedAt) {
        if (entityType == null || entityType.isBlank() || entityId <= 0 || ruleCode == null || ruleCode.isBlank()
                || severity == null || detectedAt == null) throw new IllegalArgumentException("invalid quality issue");
        var issue = new DataQualityIssue(); issue.entityType = entityType.trim(); issue.entityId = entityId;
        issue.ruleCode = ruleCode.trim(); issue.severity = severity; issue.status = DataQualityIssueStatus.OPEN;
        issue.details = details; issue.detectedAt = detectedAt; return issue;
    }
}
