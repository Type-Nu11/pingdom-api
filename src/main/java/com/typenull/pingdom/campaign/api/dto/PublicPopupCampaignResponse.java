package com.typenull.pingdom.campaign.api.dto;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import com.typenull.pingdom.campaign.domain.PopupCampaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "진행 중인 공개 팝업 캠페인 응답")
public record PublicPopupCampaignResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10") Long brandId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "진주문화재단") String brandName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "https://cdn.pingdom.com/brands/jinju.png")
        String brandLogoUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "진주 여름 팝업") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "남강변에서 진행하는 여름 체험형 팝업입니다.") String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-08-01T00:00:00Z")
        OffsetDateTime startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-08-08T00:00:00Z")
        OffsetDateTime endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "PUBLISHED", example = "PUBLISHED")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-07-20T00:00:00Z")
        OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-07-21T00:00:00Z")
        OffsetDateTime updatedAt
) {

    public static PublicPopupCampaignResponse from(PopupCampaign campaign, MerchantBrand brand) {
        return new PublicPopupCampaignResponse(
                campaign.getId(),
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                campaign.getPlaceId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getStartsAt().atOffset(ZoneOffset.UTC),
                campaign.getEndsAt().atOffset(ZoneOffset.UTC),
                campaign.getStatus().name(),
                campaign.getCreatedAt().atOffset(ZoneOffset.UTC),
                campaign.getUpdatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
