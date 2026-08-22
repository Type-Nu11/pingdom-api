package com.typenull.pingdom.payment.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.payment.api.dto.*;
import com.typenull.pingdom.payment.application.*;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/payments")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
@org.springframework.validation.annotation.Validated
public class MerchantPaymentController {
    private final PaymentCommandService commandService;
    private final PaymentQueryService queryService;

    @GetMapping
    @Operation(summary = "소유 장소 결제 목록 조회")
    public PaymentPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.listOwned(user.userId(), page, limit);
    }

    @GetMapping("/settlements")
    @Operation(summary = "정산 원장 조회")
    public SettlementLedgerPageResponse settlements(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.listSettlementLedger(user.userId(), page, limit);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "결제 전액 환불")
    public PaymentResponse refund(@PathVariable Long paymentId,
            @CurrentUser JwtAuthenticatedUser user) {
        return commandService.refund(user.userId(), paymentId);
    }
}
