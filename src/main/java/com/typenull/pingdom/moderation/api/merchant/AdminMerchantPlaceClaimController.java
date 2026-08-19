package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimReviewRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimAdminService;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMerchantPlaceClaimController {

    private final MerchantPlaceClaimAdminService claimAdminService;

    @GetMapping({
            "/admin/merchant-place-claims",
            "/admin/place-registration-applications"
    })
    @Operation(summary = "상점 장소 Claim 요청 목록 조회")
    public AdminMerchantPlaceClaimPageResponse list(
            @RequestParam(required = false) MerchantPlaceClaimStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return claimAdminService.list(status, page, limit);
    }

    @GetMapping({
            "/admin/merchant-place-claims/{claimId}",
            "/admin/place-registration-applications/{claimId}"
    })
    @Operation(summary = "상점 장소 Claim 요청 상세 조회")
    public AdminMerchantPlaceClaimResponse get(@PathVariable Long claimId) {
        return claimAdminService.get(claimId);
    }

    @PostMapping("/admin/merchant-place-claims/{claimId}/review")
    @Operation(summary = "상점 장소 Claim 요청 심사")
    public AdminMerchantPlaceClaimResponse review(
            @PathVariable Long claimId,
            @Valid @RequestBody MerchantPlaceClaimReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return claimAdminService.review(admin.userId(), claimId, request);
    }
}
