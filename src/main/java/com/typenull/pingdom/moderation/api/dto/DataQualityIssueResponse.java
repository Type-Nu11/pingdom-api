package com.typenull.pingdom.moderation.api.dto;

import com.typenull.pingdom.shared.quality.*;
import java.time.LocalDateTime;

public record DataQualityIssueResponse(String entityType, Long entityId, String ruleCode,
                                       DataQualityIssueSeverity severity, DataQualityIssueStatus status,
                                       String details, LocalDateTime detectedAt) {
    public static DataQualityIssueResponse from(DataQualityIssue issue) {
        return new DataQualityIssueResponse(issue.getEntityType(), issue.getEntityId(), issue.getRuleCode(),
                issue.getSeverity(), issue.getStatus(), issue.getDetails(), issue.getDetectedAt());
    }
}
