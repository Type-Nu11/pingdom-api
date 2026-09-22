package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import java.time.Instant;

/**
 * 클라이언트에 반환하는 증빙 메타데이터다. fileSize는 재인코딩된 저장 바이트 수이며
 * 시각은 Instant 기준이고 S3 key나 객체 URL은 노출하지 않는다.
 */
public record VisitEvidenceResponse(Long id, Long locationCheckInId, String originalFilename,
        String contentType, long fileSize, Instant createdAt, Instant expiresAt) {
    /** 저장된 증빙의 식별자·파일 정보·보관 기한을 응답에 복사한다. */
    public static VisitEvidenceResponse from(VisitEvidence evidence) {
        return new VisitEvidenceResponse(evidence.getId(), evidence.getLocationCheckInId(),
                evidence.getOriginalFilename(), evidence.getContentType(), evidence.getFileSize(),
                evidence.getCreatedAt(), evidence.getExpiresAt());
    }
}
