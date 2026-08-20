package com.typenull.pingdom.campaign.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.campaign.api.dto.BrandCreateRequest;
import com.typenull.pingdom.campaign.api.dto.BrandPageResponse;
import com.typenull.pingdom.campaign.api.dto.BrandResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignCreateRequest;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignResponse;
import com.typenull.pingdom.campaign.application.MerchantCampaignService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/campaigns")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantCampaignController {

    private final MerchantCampaignService campaignService;

    @PostMapping("/brands")
    @Operation(summary = "브랜드 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "브랜드 등록 성공"),
            @ApiResponse(responseCode = "409", description = "브랜드명 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BrandResponse> createBrand(
            @Valid @RequestBody BrandCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createBrand(user.userId(), request));
    }

    @GetMapping("/brands")
    @Operation(summary = "내 브랜드 목록 조회")
    public BrandPageResponse listBrands(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.listBrands(user.userId(), page, limit);
    }

    @PatchMapping("/brands/{brandId}")
    @Operation(summary = "내 브랜드 수정")
    public BrandResponse updateBrand(
            @PathVariable Long brandId,
            @Valid @RequestBody BrandCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.updateBrand(user.userId(), brandId, request);
    }

    @PostMapping
    @Operation(summary = "팝업 캠페인 초안 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "팝업 캠페인 등록 성공"),
            @ApiResponse(responseCode = "403", description = "장소 소유 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "브랜드를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PopupCampaignResponse> createCampaign(
            @Valid @RequestBody PopupCampaignCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 팝업 캠페인 목록 조회")
    public PopupCampaignPageResponse listCampaigns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.listCampaigns(user.userId(), page, limit);
    }

    @PatchMapping("/{campaignId}")
    @Operation(summary = "팝업 캠페인 초안 수정")
    public PopupCampaignResponse updateCampaign(
            @PathVariable Long campaignId,
            @Valid @RequestBody PopupCampaignCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.updateCampaign(user.userId(), campaignId, request);
    }

    @PostMapping("/{campaignId}/publish")
    @Operation(summary = "팝업 캠페인 공개")
    public PopupCampaignResponse publish(
            @PathVariable Long campaignId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.publish(user.userId(), campaignId);
    }

    @PostMapping("/{campaignId}/close")
    @Operation(summary = "팝업 캠페인 종료")
    public PopupCampaignResponse close(
            @PathVariable Long campaignId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return campaignService.close(user.userId(), campaignId);
    }
}
