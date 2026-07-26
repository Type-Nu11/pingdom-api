package com.typenull.pingdom.campaign.api.dto;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import com.typenull.pingdom.campaign.domain.PopupCampaign;
import com.typenull.pingdom.campaign.domain.PopupCampaignStatus;
import java.time.LocalDateTime;

public record PopupCampaignResponse(
        Long id,
        Long brandId,
        String brandName,
        String brandLogoUrl,
        Long placeId,
        String title,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        PopupCampaignStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PopupCampaignResponse from(PopupCampaign campaign, MerchantBrand brand) {
        return new PopupCampaignResponse(
                campaign.getId(),
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                campaign.getPlaceId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                campaign.getStatus(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
