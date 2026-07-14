package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfilePageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerAdminService;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-owners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMerchantOwnerController {

    private final MerchantOwnerAdminService adminService;

    @GetMapping
    @Operation(summary = "Merchant Owner 신청 목록 조회")
    public MerchantOwnerProfilePageResponse list(
            @RequestParam(required = false) MerchantOwnerStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminService.list(status, page, limit);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Merchant Owner 신청 상세 조회")
    public MerchantOwnerProfileResponse get(@PathVariable Long userId) {
        return adminService.get(userId);
    }

    @PostMapping("/{userId}/approve")
    @Operation(summary = "Merchant Owner 승인")
    public MerchantOwnerProfileResponse approve(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return adminService.approve(admin.userId(), userId, request);
    }

    @PostMapping("/{userId}/reject")
    @Operation(summary = "Merchant Owner 거절")
    public MerchantOwnerProfileResponse reject(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return adminService.reject(admin.userId(), userId, request);
    }

    @PostMapping("/{userId}/revoke")
    @Operation(summary = "Merchant Owner 권한 회수")
    public MerchantOwnerProfileResponse revoke(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return adminService.revoke(admin.userId(), userId, request);
    }

    @PutMapping("/{userId}/places")
    @Operation(summary = "Merchant Owner 장소 연결 변경")
    public MerchantOwnerProfileResponse replacePlaces(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerPlaceUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return adminService.replacePlaces(admin.userId(), userId, request);
    }
}
