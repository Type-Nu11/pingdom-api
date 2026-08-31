package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/** Presigned URL로 발급한 탐색 미디어 객체의 등록 가능 범위를 보존합니다. */
@Getter
@Entity
@Table(name = "merchant_place_media_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantPlaceMediaUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_place_media_upload_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(name = "s3_key", nullable = false, unique = true, length = 500)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceMediaUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MerchantPlaceMediaUpload(
            Long placeId,
            Long issuedByUserId,
            String s3Key,
            String contentType,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        this.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        this.issuedByUserId = Objects.requireNonNull(issuedByUserId, "issuedByUserId must not be null");
        this.s3Key = requireText(s3Key, "s3Key must not be blank");
        this.contentType = requireText(contentType, "contentType must not be blank");
        this.status = MerchantPlaceMediaUploadStatus.ISSUED;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static MerchantPlaceMediaUpload issue(
            Long placeId,
            Long issuedByUserId,
            String s3Key,
            String contentType,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        return new MerchantPlaceMediaUpload(placeId, issuedByUserId, s3Key, contentType, expiresAt, createdAt);
    }

    public boolean isRegistrableBy(Long placeId, Long userId, LocalDateTime now) {
        return status == MerchantPlaceMediaUploadStatus.ISSUED
                && this.placeId.equals(placeId)
                && issuedByUserId.equals(userId)
                && expiresAt.isAfter(now);
    }

    public boolean matchesContentType(String contentType) {
        return StringUtils.hasText(contentType) && this.contentType.equalsIgnoreCase(contentType.trim());
    }

    public void markRegistered(LocalDateTime now) {
        if (status != MerchantPlaceMediaUploadStatus.ISSUED) {
            throw new IllegalStateException("발급된 탐색 미디어만 등록할 수 있습니다.");
        }
        status = MerchantPlaceMediaUploadStatus.REGISTERED;
        registeredAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
