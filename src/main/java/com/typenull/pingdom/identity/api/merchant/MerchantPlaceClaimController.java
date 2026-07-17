package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceClaimService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/merchant-owner/place-claims")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantPlaceClaimController {

    private final MerchantPlaceClaimService claimService;

    @PostMapping
    @Operation(summary = "상점 장소 Claim 요청")
    public ResponseEntity<MerchantPlaceClaimResponse> create(
            @Valid @RequestBody MerchantPlaceClaimRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 상점 장소 Claim 요청 목록 조회")
    public MerchantPlaceClaimPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return claimService.list(user.userId(), page, limit);
    }

    @GetMapping("/{claimId}")
    @Operation(summary = "내 상점 장소 Claim 요청 상세 조회")
    public MerchantPlaceClaimResponse get(
            @PathVariable Long claimId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return claimService.get(user.userId(), claimId);
    }

    @PostMapping("/{claimId}/cancel")
    @Operation(summary = "상점 장소 Claim 요청 취소")
    public MerchantPlaceClaimResponse cancel(
            @PathVariable Long claimId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return claimService.cancel(user.userId(), claimId);
    }
}
