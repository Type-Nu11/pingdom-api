package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
/** 관광객 예약 생성·조회·취소 요청을 예약 서비스로 전달합니다. */
public class ReservationController {
    private final ReservationService service;

    @PostMapping
    @Operation(summary = "예약 생성")
    @ApiResponse(responseCode = "201", description = "예약 생성 성공")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 예약 목록 조회")
    public ReservationPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{reservationId}")
    @Operation(
            summary = "내 예약 상세 조회",
            description = """
                    활성 일반 사용자는 본인 예약만 조회할 수 있습니다.
                    아직 확정되지 않은 예약의 confirmedAt과 취소되지 않은 예약의 canceledAt은 null입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 예약 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아니거나 다른 사용자의 예약",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "TOURIST_ACCOUNT_REQUIRED",
                                            value = """
                                                    {"message":"일반 사용자 계정만 예약할 수 있습니다.","code":"TOURIST_ACCOUNT_REQUIRED"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "RESERVATION_FORBIDDEN",
                                            value = """
                                                    {"message":"이 예약을 처리할 권한이 없습니다.","code":"RESERVATION_FORBIDDEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "예약을 찾을 수 없음 (RESERVATION_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "RESERVATION_NOT_FOUND",
                                    value = """
                                            {"message":"예약을 찾을 수 없습니다.","code":"RESERVATION_NOT_FOUND"}
                                            """
                            )
                    )
            )
    })
    public ReservationResponse get(@PathVariable Long reservationId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.getMine(user.userId(), reservationId);
    }

    @PostMapping("/{reservationId}/cancel")
    @Operation(summary = "내 예약 취소")
    public ReservationResponse cancel(@PathVariable Long reservationId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.cancelMine(user.userId(), reservationId);
    }
}
