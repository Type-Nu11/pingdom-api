package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.shared.security.annotation.ApprovedMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerProfileService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner")
@RequiredArgsConstructor
@ApprovedMerchantOwnerOnly
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantOwnerController {

    private final MerchantOwnerProfileService profileService;

    @GetMapping("/me")
    @Operation(summary = "승인된 Merchant Owner 프로필 조회", description = "DB의 현재 역할과 승인 상태를 확인합니다.")
    public MerchantOwnerProfileResponse getActiveProfile(
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return profileService.get(user.userId());
    }
}
