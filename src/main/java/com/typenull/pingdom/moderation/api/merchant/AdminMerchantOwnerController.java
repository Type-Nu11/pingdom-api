package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOnboardingUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceQualityUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfilePageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerAdminService;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@AdminOnly
@Tag(name = "Admin", description = "관리자 전용 API")
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

    @GetMapping("/{userId}/places")
    @Operation(summary = "Merchant Owner 연결 장소 및 운영 품질 조회")
    public List<MerchantOwnerPlaceResponse> listPlaces(@PathVariable Long userId) {
        return adminService.listPlaces(userId);
    }

    @PostMapping("/{userId}/approve")
    @Operation(
            summary = "Merchant Owner 승인",
            description = "승인 사유만 처리합니다. 장소 연결은 변경하지 않으며, 승인 후 연결이 필요하면 "
                    + "PUT /admin/merchant-owners/{userId}/places로 전체 장소 목록을 별도 요청해야 합니다. "
                    + "승인과 장소 연결은 별도 트랜잭션입니다."
    )
    public MerchantOwnerProfileResponse approve(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.approve(admin.userId(), userId, request);
    }

    @PostMapping("/{userId}/reject")
    @Operation(summary = "Merchant Owner 반려")
    public MerchantOwnerProfileResponse reject(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.reject(admin.userId(), userId, request);
    }

    @PostMapping("/{userId}/revoke")
    @Operation(summary = "Merchant Owner 권한 회수")
    public MerchantOwnerProfileResponse revoke(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.revoke(admin.userId(), userId, request);
    }

    @PutMapping("/{userId}/places")
    @Operation(
            summary = "Merchant Owner 장소 연결 변경",
            description = "현재 연결 장소를 요청의 placeIds 전체로 교체합니다. 빈 목록은 모든 장소 연결을 해제합니다. "
                    + "Merchant Owner 승인과는 별도 요청 및 별도 트랜잭션입니다."
    )
    public MerchantOwnerProfileResponse replacePlaces(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOwnerPlaceUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.replacePlaces(admin.userId(), userId, request);
    }

    @PutMapping("/{userId}/onboarding")
    @Operation(summary = "Merchant Owner 온보딩 완료도 변경")
    public MerchantOwnerProfileResponse updateOnboarding(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantOnboardingUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.updateOnboarding(admin.userId(), userId, request);
    }

    @PutMapping("/{userId}/places/{placeId}/quality")
    @Operation(summary = "Merchant Owner 장소 운영 품질 지표 변경")
    public MerchantOwnerPlaceResponse updateOperationalQuality(
            @PathVariable Long userId,
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantOwnerPlaceQualityUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return adminService.updateOperationalQuality(admin.userId(), userId, placeId, request);
    }
}
