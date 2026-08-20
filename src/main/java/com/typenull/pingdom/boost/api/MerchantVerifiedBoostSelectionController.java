package com.typenull.pingdom.boost.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionCreateRequest;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionResponse;
import com.typenull.pingdom.boost.application.MerchantVerifiedBoostSelectionService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/verified-boost-selections")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantVerifiedBoostSelectionController {

    private final MerchantVerifiedBoostSelectionService service;

    @PostMapping
    @Operation(summary = "Verified Boost 상품 선택")
    public ResponseEntity<VerifiedBoostSelectionResponse> select(
            @Valid @RequestBody VerifiedBoostSelectionCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.select(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Verified Boost 선택 목록 조회")
    public VerifiedBoostSelectionPageResponse list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.list(user.userId(), page, limit);
    }
}
