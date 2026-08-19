package com.typenull.pingdom.campaign.api;

import com.typenull.pingdom.campaign.api.dto.PopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignResponse;
import com.typenull.pingdom.campaign.application.PopupCampaignQueryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/popup-campaigns")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
/** 노출 가능한 팝업 캠페인을 조회하는 API 진입점입니다. */
public class PopupCampaignController {

    private final PopupCampaignQueryService queryService;

    @GetMapping
    @Operation(summary = "진행 중인 공개 팝업 캠페인 목록 조회")
    public PopupCampaignPageResponse list(
            @RequestParam(required = false) Long placeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.list(placeId, page, limit);
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "진행 중인 공개 팝업 캠페인 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "팝업 캠페인 조회 성공"),
            @ApiResponse(responseCode = "404", description = "캠페인을 찾을 수 없거나 공개 대상이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PopupCampaignResponse get(@PathVariable Long campaignId) {
        return queryService.get(campaignId);
    }
}
