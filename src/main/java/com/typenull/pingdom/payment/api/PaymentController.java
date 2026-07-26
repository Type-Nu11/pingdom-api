package com.typenull.pingdom.payment.api;

import com.typenull.pingdom.payment.api.dto.*;
import com.typenull.pingdom.payment.application.*;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class PaymentController {
    private final PaymentCommandService commandService;
    private final PaymentQueryService queryService;

    @PostMapping
    @Operation(summary = "예약 결제 요청")
    @ApiResponse(responseCode = "201", description = "결제 거래 생성 성공")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 결제 목록 조회")
    public PaymentPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return queryService.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "내 결제 상세 조회")
    public PaymentResponse get(@PathVariable Long paymentId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return queryService.getMine(user.userId(), paymentId);
    }
}
