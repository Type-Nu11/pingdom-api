package com.typenull.pingdom.place.domain.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "place_registration_application_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRegistrationAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_registration_attachment_application"))
    private PlaceRegistrationApplication application;

    @Column(name = "file_id", length = 100)
    private String fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private PlaceRegistrationAttachmentType documentType;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "retention_expires_at")
    private LocalDateTime retentionExpiresAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(nullable = false)
    private long version;

    private PlaceRegistrationAttachment(PlaceRegistrationApplication application, String fileId,
                                        PlaceRegistrationAttachmentType documentType, String storageKey,
                                        String originalFilename, String contentType, long fileSize,
                                        String fileHash, Long uploadedByUserId, LocalDateTime uploadedAt,
                                        LocalDateTime retentionExpiresAt, int displayOrder) {
        this.application = Objects.requireNonNull(application, "application must not be null");
        this.fileId = trimToNull(fileId);
        this.documentType = Objects.requireNonNull(documentType, "documentType must not be null");
        this.storageKey = requireStorageKey(storageKey);
        this.originalFilename = requireText(originalFilename, "originalFilename must not be blank");
        this.contentType = requireText(contentType, "contentType must not be blank");
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive");
        }
        this.fileSize = fileSize;
        this.fileHash = requireHash(fileHash);
        this.uploadedByUserId = Objects.requireNonNull(uploadedByUserId, "uploadedByUserId must not be null");
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        this.retentionExpiresAt = retentionExpiresAt;
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        this.displayOrder = displayOrder;
    }

    public static PlaceRegistrationAttachment create(PlaceRegistrationApplication application, String fileId,
                                                      PlaceRegistrationAttachmentType documentType, String storageKey,
                                                      String originalFilename, String contentType, long fileSize,
                                                      String fileHash, Long uploadedByUserId, LocalDateTime uploadedAt,
                                                      LocalDateTime retentionExpiresAt, int displayOrder) {
        return new PlaceRegistrationAttachment(application, fileId, documentType, storageKey, originalFilename,
                contentType, fileSize, fileHash, uploadedByUserId, uploadedAt, retentionExpiresAt, displayOrder);
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public void markDeleted(LocalDateTime deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt must not be null");
    }

    public boolean isRetentionExpired(LocalDateTime now) {
        return retentionExpiresAt != null && !retentionExpiresAt.isAfter(now);
    }

    private static String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String requireStorageKey(String value) {
        String normalized = requireText(value, "storageKey must not be blank");
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("://")) {
            throw new IllegalArgumentException("storageKey must be a relative object key");
        }
        return normalized;
    }

    private static String requireHash(String value) {
        String normalized = requireText(value, "fileHash must not be blank");
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("fileHash must be a SHA-256 hex value");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
