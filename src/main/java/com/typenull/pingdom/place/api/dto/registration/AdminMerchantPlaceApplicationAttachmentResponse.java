package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.time.LocalDateTime;

/** 관리자 심사 화면에서 사용하는 통합 신청 첨부파일 메타데이터입니다. */
public record AdminMerchantPlaceApplicationAttachmentResponse(
        Long id,
        PlaceRegistrationAttachmentType documentType,
        String originalFilename,
        String contentType,
        long fileSize,
        LocalDateTime uploadedAt,
        LocalDateTime retentionExpiresAt,
        int displayOrder
) {
    public static AdminMerchantPlaceApplicationAttachmentResponse from(PlaceRegistrationAttachment attachment) {
        return new AdminMerchantPlaceApplicationAttachmentResponse(
                attachment.getId(),
                attachment.getDocumentType(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getRetentionExpiresAt(),
                attachment.getDisplayOrder()
        );
    }
}
