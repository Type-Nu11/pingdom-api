package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/reservations")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.MERCHANT)
@Tag(name = SwaggerTagCatalog.MERCHANT_RESERVATION)
@org.springframework.validation.annotation.Validated
public class MerchantReservationController {
    private final ReservationService service;

    @GetMapping
    @Operation(summary = "소유 장소 예약 목록 조회")
    public ReservationPageResponse list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservationTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listOwned(user.userId(), status, placeId, reservationFrom, reservationTo, page, limit);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "소유 장소 예약 상세 및 처리 이력 조회")
    public MerchantReservationDetailResponse get(@PathVariable Long reservationId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.getOwned(user.userId(), reservationId);
    }

    @PostMapping("/{reservationId}/confirm")
    @Operation(summary = "소유 장소 예약 승인", description = "현재 장소 소유자만 대기 예약을 승인할 수 있습니다. 승인 사유는 선택입니다.")
    public ReservationResponse confirm(@PathVariable Long reservationId,
            @Valid @RequestBody(required = false) ReservationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.confirmOwned(user.userId(), reservationId, request == null ? null : request.reason());
    }

    @PostMapping("/{reservationId}/reject")
    @Operation(summary = "소유 장소 예약 반려", description = "현재 장소 소유자만 대기 예약을 반려할 수 있습니다. 반려 사유는 필수입니다.")
    public ReservationResponse reject(@PathVariable Long reservationId,
            @Valid @RequestBody ReservationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.rejectOwned(user.userId(), reservationId, request.reason());
    }

    @PostMapping("/{reservationId}/cancel")
    @Operation(summary = "예약 취소")
    public ReservationResponse cancel(@PathVariable Long reservationId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.cancelOwned(user.userId(), reservationId);
    }
}
