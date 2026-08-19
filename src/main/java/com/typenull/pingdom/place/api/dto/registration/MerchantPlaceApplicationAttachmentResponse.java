package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import java.time.LocalDateTime;

/** 통합 신청 조회에 사용하는 첨부파일 응답입니다. 저장소 내부 key는 노출하지 않습니다. */
public record MerchantPlaceApplicationAttachmentResponse(
        Long id,
        String fileId,
        PlaceRegistrationAttachmentType documentType,
        String originalFilename,
        String contentType,
        long fileSize,
        LocalDateTime uploadedAt,
        LocalDateTime retentionExpiresAt,
        int displayOrder
) {
    public static MerchantPlaceApplicationAttachmentResponse from(PlaceRegistrationAttachment attachment) {
        return new MerchantPlaceApplicationAttachmentResponse(
                attachment.getId(),
                attachment.getFileId(),
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
