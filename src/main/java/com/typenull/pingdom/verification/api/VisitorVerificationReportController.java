package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.VisitorVerificationReportCorrectionService;
import com.typenull.pingdom.verification.application.VisitorVerificationReportService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/visitor-verification-reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class VisitorVerificationReportController {
    private final VisitorVerificationReportService service;
    private final VisitorVerificationReportCorrectionService correctionService;

    @PostMapping
    @Operation(summary = "방문자 검증 제보 생성")
    @ApiResponse(responseCode = "201", description = "제보 생성 성공")
    public ResponseEntity<MyVisitorVerificationReportResponse> submit(
            @Valid @RequestBody VisitorVerificationReportCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 방문자 검증 제보 목록 조회")
    public MyVisitorVerificationReportPageResponse listMine(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "내 방문자 검증 제보 상세 조회")
    public MyVisitorVerificationReportResponse getMine(@PathVariable Long reportId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.getMine(user.userId(), reportId);
    }

    @PostMapping("/{reportId}/corrections")
    @Operation(summary = "방문자 검증 제보 정정 제출")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제보 정정 제출 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 정정 내용 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 소유 제보가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "정정 불가 상태 또는 처리 중인 정정 존재",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MyVisitorVerificationReportCorrectionResponse> submitCorrection(
            @PathVariable Long reportId,
            @Valid @RequestBody VisitorVerificationReportCorrectionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(correctionService.submit(user.userId(), reportId, request));
    }

    @GetMapping("/{reportId}/corrections")
    @Operation(summary = "내 방문자 검증 제보 정정 이력 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제보 정정 이력 조회 성공"),
            @ApiResponse(responseCode = "400", description = "페이지 요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 소유 제보가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MyVisitorVerificationReportCorrectionPageResponse listCorrections(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return correctionService.listMine(user.userId(), reportId, page, limit);
    }
}
