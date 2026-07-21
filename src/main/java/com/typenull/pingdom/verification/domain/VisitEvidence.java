package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "visit_evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_check_in_id", nullable = false)
    private Long locationCheckInId;

    @Column(name = "tourist_user_id", nullable = false)
    private Long touristUserId;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static VisitEvidence create(Long locationCheckInId, Long touristUserId, String s3Key,
            String originalFilename, String contentType, long fileSize, Instant createdAt, Instant expiresAt) {
        VisitEvidence evidence = new VisitEvidence();
        evidence.locationCheckInId = Objects.requireNonNull(locationCheckInId);
        evidence.touristUserId = Objects.requireNonNull(touristUserId);
        evidence.s3Key = Objects.requireNonNull(s3Key);
        evidence.originalFilename = Objects.requireNonNull(originalFilename);
        evidence.contentType = Objects.requireNonNull(contentType);
        evidence.fileSize = fileSize;
        evidence.createdAt = Objects.requireNonNull(createdAt);
        evidence.expiresAt = Objects.requireNonNull(expiresAt);
        if (fileSize <= 0 || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("증빙 파일 크기와 만료 시각이 올바르지 않습니다.");
        }
        return evidence;
    }
}
