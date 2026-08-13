package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import java.time.LocalDateTime;

public record MerchantPlaceClaimAttachmentResponse(
        Long id, MerchantPlaceClaimAttachmentType documentType, String contentType,
        long fileSize, int displayOrder, LocalDateTime createdAt) {
    public static MerchantPlaceClaimAttachmentResponse from(MerchantPlaceClaimAttachment attachment) {
        return new MerchantPlaceClaimAttachmentResponse(
                attachment.getId(), attachment.getDocumentType(), attachment.getContentType(),
                attachment.getFileSize(), attachment.getDisplayOrder(), attachment.getCreatedAt());
    }
}
