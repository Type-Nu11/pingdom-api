package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationReviewRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantVerificationAdminService;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMerchantVerificationController {

    private final MerchantVerificationAdminService verificationAdminService;

    @GetMapping
    @Operation(summary = "Merchant 검증 신청 목록 조회")
    public AdminMerchantVerificationPageResponse list(
            @RequestParam(required = false) MerchantVerificationStatus identityStatus,
            @RequestParam(required = false) MerchantVerificationStatus businessStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return verificationAdminService.list(identityStatus, businessStatus, page, limit);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Merchant 검증 신청 상세 조회")
    public AdminMerchantVerificationResponse get(@PathVariable Long userId) {
        return verificationAdminService.get(userId);
    }

    @PostMapping("/{userId}/review")
    @Operation(summary = "Merchant 신원 및 사업자 수동 심사")
    public AdminMerchantVerificationResponse review(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantVerificationReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return verificationAdminService.review(admin.userId(), userId, request);
    }
}
