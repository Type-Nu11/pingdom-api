package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.VisitorVerificationReportCorrectionService;
import com.typenull.pingdom.verification.application.VisitorVerificationReportService;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/visitor-verification-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
@org.springframework.validation.annotation.Validated
public class AdminVisitorVerificationReportController {
    private final VisitorVerificationReportService service;
    private final VisitorVerificationReportCorrectionService correctionService;

    @GetMapping
    @Operation(summary = "방문자 검증 제보 목록 조회")
    public VisitorVerificationReportPageResponse list(
            @RequestParam(required = false) VisitorVerificationReportStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin) {
        return service.listForAdmin(admin.userId(), status, page, limit);
    }

    @PostMapping("/{reportId}/review")
    @Operation(summary = "방문자 검증 제보 심사")
    public VisitorVerificationReportResponse review(@PathVariable Long reportId,
            @Valid @RequestBody VisitorVerificationReportReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin) {
        return service.review(admin.userId(), reportId, request);
    }

    @GetMapping("/corrections")
    @Operation(summary = "방문자 검증 제보 정정 목록 조회")
    public VisitorVerificationReportCorrectionPageResponse listCorrections(
            @RequestParam(required = false) VisitorVerificationReportCorrectionStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin) {
        return correctionService.listForAdmin(admin.userId(), status, page, limit);
    }

    @PostMapping("/corrections/{correctionId}/review")
    @Operation(summary = "방문자 검증 제보 정정 심사")
    public VisitorVerificationReportCorrectionResponse reviewCorrection(
            @PathVariable Long correctionId,
            @Valid @RequestBody VisitorVerificationReportCorrectionReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin) {
        return correctionService.review(admin.userId(), correctionId, request);
    }
}
