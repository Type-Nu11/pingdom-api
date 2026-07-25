package com.typenull.pingdom.campaign.api.dto;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String name,
        String description,
        String logoUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BrandResponse from(MerchantBrand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getLogoUrl(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
