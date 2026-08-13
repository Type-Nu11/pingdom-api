package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.time.LocalDateTime;

public record PlaceRegistrationAttachmentResponse(
        Long id,
        String fileId,
        PlaceRegistrationAttachmentType documentType,
        String storageKey,
        String originalFilename,
        String contentType,
        long fileSize,
        String fileHash,
        Long uploadedByUserId,
        LocalDateTime uploadedAt,
        LocalDateTime retentionExpiresAt,
        int displayOrder
) {
    public static PlaceRegistrationAttachmentResponse from(PlaceRegistrationAttachment attachment) {
        return new PlaceRegistrationAttachmentResponse(attachment.getId(), attachment.getFileId(),
                attachment.getDocumentType(), attachment.getStorageKey(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getFileSize(), attachment.getFileHash(),
                attachment.getUploadedByUserId(), attachment.getUploadedAt(), attachment.getRetentionExpiresAt(),
                attachment.getDisplayOrder());
    }
}
