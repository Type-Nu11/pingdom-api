package com.typenull.pingdom.campaign.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "진행 중인 공개 팝업 캠페인 목록 응답")
public record PublicPopupCampaignPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PublicPopupCampaignResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42") long totalCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean hasNext
) {
}
