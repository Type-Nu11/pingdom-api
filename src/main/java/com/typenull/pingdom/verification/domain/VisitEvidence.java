package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 체크인 증빙의 소유자·S3 key·이미지 메타데이터·보관 기한을 보관.
 * 파일 바이트는 S3에 있으며 S3 객체 삭제는 DB 행 삭제와 별도.
 */
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

    /**
     * 필수 메타데이터를 요구하고 양수 크기와 생성 시각 이후의 만료 시각으로 증빙을 생성.
     * 체크인 소유권과 중복 여부는 이 객체의 생성만으로 확인하지 않으며 서비스·DB가 담당.
     */
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
