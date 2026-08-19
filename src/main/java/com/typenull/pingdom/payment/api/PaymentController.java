package com.typenull.pingdom.payment.api;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
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
@RequestMapping("/payments")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
/** 결제 내역과 결제 상태 조회 API의 진입점입니다. */
public class PaymentController {
    private final PaymentQueryService queryService;

    @GetMapping
    @Operation(summary = "내 결제 목록 조회")
    public PaymentPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "내 결제 상세 조회")
    public PaymentResponse get(@PathVariable Long paymentId,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.getMine(user.userId(), paymentId);
    }
}
