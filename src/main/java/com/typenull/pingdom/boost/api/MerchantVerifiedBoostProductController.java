package com.typenull.pingdom.boost.api;

import com.typenull.pingdom.boost.api.dto.MerchantVerifiedBoostProductPageResponse;
import com.typenull.pingdom.boost.application.VerifiedBoostProductService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/verified-boost-products")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantVerifiedBoostProductController {

    private final VerifiedBoostProductService service;

    @GetMapping
    @Operation(summary = "선택 가능한 Verified Boost 활성 상품 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = MerchantVerifiedBoostProductPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "활성 Merchant Owner 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MerchantVerifiedBoostProductPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return MerchantVerifiedBoostProductPageResponse.from(service.listActive(page, limit));
    }
}
