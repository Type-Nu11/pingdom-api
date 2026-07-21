package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import java.time.Instant;

public record VisitEvidenceResponse(Long id, Long locationCheckInId, String originalFilename,
        String contentType, long fileSize, Instant createdAt, Instant expiresAt) {
    public static VisitEvidenceResponse from(VisitEvidence evidence) {
        return new VisitEvidenceResponse(evidence.getId(), evidence.getLocationCheckInId(),
                evidence.getOriginalFilename(), evidence.getContentType(), evidence.getFileSize(),
                evidence.getCreatedAt(), evidence.getExpiresAt());
    }
}
