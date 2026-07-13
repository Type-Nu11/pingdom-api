package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerProfileService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/merchant-owner-profile")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MerchantOwnerProfileController {

    private final MerchantOwnerProfileService profileService;

    @PostMapping
    @Operation(summary = "Merchant Owner 신청", description = "프로필을 생성하거나 거절·회수된 신청을 다시 제출합니다.")
    public ResponseEntity<MerchantOwnerProfileResponse> apply(
            @Valid @RequestBody MerchantOwnerProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.apply(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Merchant Owner 신청 조회")
    public MerchantOwnerProfileResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return profileService.get(user.userId());
    }

    @PutMapping
    @Operation(summary = "내 Merchant Owner 프로필 수정")
    public MerchantOwnerProfileResponse update(
            @Valid @RequestBody MerchantOwnerProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return profileService.update(user.userId(), request);
    }
}
