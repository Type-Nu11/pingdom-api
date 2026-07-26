package com.typenull.pingdom.campaign.api.dto;

import java.util.List;

public record PopupCampaignPageResponse(
        List<PopupCampaignResponse> items,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
