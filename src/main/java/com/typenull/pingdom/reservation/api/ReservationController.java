package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.application.ReservationService;
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
@RequestMapping("/reservations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class ReservationController {
    private final ReservationService service;

    @PostMapping
    @Operation(summary = "예약 생성")
    @ApiResponse(responseCode = "201", description = "예약 생성 성공")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 예약 목록 조회")
    public ReservationPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "내 예약 상세 조회")
    public ReservationResponse get(@PathVariable Long reservationId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.getMine(user.userId(), reservationId);
    }

    @PostMapping("/{reservationId}/cancel")
    @Operation(summary = "내 예약 취소")
    public ReservationResponse cancel(@PathVariable Long reservationId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.cancelMine(user.userId(), reservationId);
    }
}
