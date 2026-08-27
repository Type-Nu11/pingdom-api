package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.VisitVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 체류 인증은 원본 위치 좌표를 보관하지 않고 서버가 판정한 거리와 시각만 저장합니다. */
@RestController
@RequestMapping("/visit-verification-sessions")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class VisitVerificationSessionController {
    private final VisitVerificationService service;

    @PostMapping
    @Operation(summary = "체류 기반 방문 인증 시작",
            description = "장소 반경 안의 첫 위치 관측으로 인증 세션을 시작합니다. 앱은 nextObservationRecommendedAt 이전에 다음 위치를 제출해야 하며, 원본 좌표는 저장하지 않습니다.")
    @ApiResponse(responseCode = "201", description = "인증 세션 시작 또는 진행 중·완료 세션 재반환")
    @ApiResponse(responseCode = "400", description = "관측 시각 또는 GPS 정확도 검증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "활성 관광객 계정이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "인증 가능한 장소를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "동일 장소의 진행 중인 인증 세션이 동시에 생성됨",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "장소 인증 허용 반경 밖",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<VisitVerificationSessionResponse> start(@Valid @RequestBody VisitVerificationStartRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(user.userId(), request));
    }

    @PostMapping("/{sessionId}/observations")
    @Operation(summary = "체류 인증 위치 관측 제출",
            description = "서버 수신 시각과 연속 관측 간격으로 체류 시간을 계산합니다. 반경 이탈은 PROXIMITY_LOST, 관측 공백 또는 세션 기한 초과는 EXPIRED로 반환됩니다.")
    @ApiResponse(responseCode = "200", description = "진행 상태 또는 완료 상태")
    @ApiResponse(responseCode = "400", description = "관측 시각 또는 GPS 정확도 검증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "활성 관광객 계정이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 소유 인증 세션 또는 장소를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "일일 완료 방문 인증이 이미 존재함",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public VisitVerificationSessionResponse submitObservation(@PathVariable Long sessionId,
            @Valid @RequestBody VisitVerificationObservationRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.submitObservation(user.userId(), sessionId, request);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "내 체류 기반 방문 인증 상태 조회")
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "활성 관광객 계정이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "본인 소유 인증 세션을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public VisitVerificationSessionResponse get(@PathVariable Long sessionId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.get(user.userId(), sessionId);
    }
}
