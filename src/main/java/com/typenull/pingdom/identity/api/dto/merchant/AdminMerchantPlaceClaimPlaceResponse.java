package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import java.util.List;

public record AdminMerchantPlaceClaimPlaceResponse(
        Long id,
        String name,
        String address,
        String category,
        Double latitude,
        Double longitude,
        String imageUrl,
        List<AdminMerchantPlaceClaimAttachmentResponse> attachments,
        AdminMapPlaceDuplicateDetailResponse duplicateCandidates
) {
}
