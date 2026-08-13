package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;

public record AdminMerchantPlaceClaimAttachmentResponse(
        Long id,
        MerchantPlaceClaimAttachmentType documentType,
        String contentType,
        long fileSize,
        int displayOrder
) {
    public static AdminMerchantPlaceClaimAttachmentResponse from(MerchantPlaceClaimAttachment attachment) {
        return new AdminMerchantPlaceClaimAttachmentResponse(
                attachment.getId(), attachment.getDocumentType(), attachment.getContentType(),
                attachment.getFileSize(), attachment.getDisplayOrder());
    }
}
