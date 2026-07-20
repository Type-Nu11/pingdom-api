package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.reservation.api.dto.ReservationResponse;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/reservations")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class MerchantReservationController {
    private final ReservationService service;

    @GetMapping
    @Operation(summary = "소유 장소 예약 목록 조회")
    public com.typenull.pingdom.reservation.api.dto.ReservationPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listOwned(user.userId(), page, limit);
    }

    @PostMapping("/{reservationId}/confirm")
    @Operation(summary = "예약 확정")
    public ReservationResponse confirm(@PathVariable Long reservationId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.confirm(user.userId(), reservationId);
    }

    @PostMapping("/{reservationId}/cancel")
    @Operation(summary = "예약 취소")
    public ReservationResponse cancel(@PathVariable Long reservationId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.cancelOwned(user.userId(), reservationId);
    }
}
