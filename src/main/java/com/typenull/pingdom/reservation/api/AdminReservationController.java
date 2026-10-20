package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@ApiAudience(ApiAudience.Group.ADMIN)
@Tag(name = SwaggerTagCatalog.ADMIN_RESERVATION)
@org.springframework.validation.annotation.Validated
public class AdminReservationController {
    private final ReservationService service;

    @GetMapping
    @Operation(summary = "관리자 예약 기록 목록 조회")
    public AdminReservationPageResponse list(@RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long placeId, @RequestParam(required = false) Long merchantOwnerUserId,
            @RequestParam(required = false) Long touristUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.listForAdmin(status, placeId, merchantOwnerUserId, touristUserId, reservationFrom,
                reservationTo, page, limit);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "관리자 예약 기록 상세 조회")
    public AdminReservationResponse get(@PathVariable Long reservationId) { return service.getForAdmin(reservationId); }

}
