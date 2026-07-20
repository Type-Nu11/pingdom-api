package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.VisitorVerificationReportService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
}
