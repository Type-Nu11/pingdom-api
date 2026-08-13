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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "merchant_place_claim_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantPlaceClaimAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private MerchantPlaceClaimAttachmentType documentType;

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

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private MerchantPlaceClaimAttachment(
            Long claimId, MerchantPlaceClaimAttachmentType documentType, String storageKey,
            String originalFilename, String contentType, long fileSize, String fileHash,
            int displayOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.claimId = claimId;
        this.documentType = documentType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MerchantPlaceClaimAttachment create(
            Long claimId, MerchantPlaceClaimAttachmentType documentType, String storageKey,
            String originalFilename, String contentType, long fileSize, String fileHash,
            int displayOrder, LocalDateTime now) {
        return MerchantPlaceClaimAttachment.builder()
                .claimId(claimId).documentType(documentType).storageKey(storageKey)
                .originalFilename(originalFilename).contentType(contentType).fileSize(fileSize)
                .fileHash(fileHash).displayOrder(displayOrder).createdAt(now).updatedAt(now).build();
    }

    public void changeDisplayOrder(int displayOrder, LocalDateTime now) {
        if (displayOrder < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        this.displayOrder = displayOrder;
        this.updatedAt = now;
    }
}
