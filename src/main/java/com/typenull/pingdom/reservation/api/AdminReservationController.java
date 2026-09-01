package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/reservations")
@RequiredArgsConstructor
@AdminOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "관리자 전용 API")
@org.springframework.validation.annotation.Validated
public class AdminReservationController {
    private final ReservationService service;

    @GetMapping
    @Operation(summary = "관리자 예약 심사 목록 조회")
    public AdminReservationPageResponse list(@RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long placeId, @RequestParam(required = false) Long merchantOwnerUserId,
            @RequestParam(required = false) Long touristUserId, @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.listForAdmin(status, placeId, merchantOwnerUserId, touristUserId, productId, reservationFrom,
                reservationTo, page, limit);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "관리자 예약 심사 상세 조회")
    public AdminReservationResponse get(@PathVariable Long reservationId) { return service.getForAdmin(reservationId); }

    @PostMapping("/{reservationId}/confirm")
    @Operation(summary = "관리자 예약 승인")
    public AdminReservationResponse confirm(@PathVariable Long reservationId,
            @Valid @RequestBody(required = false) ReservationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin) {
        return service.confirmByAdmin(admin.userId(), reservationId, request == null ? null : request.reason());
    }

    @PostMapping("/{reservationId}/reject")
    @Operation(summary = "관리자 예약 반려")
    public AdminReservationResponse reject(@PathVariable Long reservationId, @Valid @RequestBody ReservationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin) { return service.rejectByAdmin(admin.userId(), reservationId, request.reason()); }
}
