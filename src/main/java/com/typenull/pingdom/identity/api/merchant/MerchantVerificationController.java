package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantVerificationService;
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
@RequestMapping("/users/me/merchant-verification")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class MerchantVerificationController {

    private final MerchantVerificationService verificationService;

    @PostMapping
    @Operation(summary = "Merchant 신원 및 사업자 검증 신청")
    public ResponseEntity<MerchantVerificationResponse> apply(
            @Valid @RequestBody MerchantVerificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(verificationService.apply(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Merchant 신원 및 사업자 검증 조회")
    public MerchantVerificationResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return verificationService.get(user.userId());
    }

    @PutMapping
    @Operation(summary = "내 Merchant 신원 및 사업자 검증 신청 수정")
    public MerchantVerificationResponse update(
            @Valid @RequestBody MerchantVerificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return verificationService.update(user.userId(), request);
    }
}
