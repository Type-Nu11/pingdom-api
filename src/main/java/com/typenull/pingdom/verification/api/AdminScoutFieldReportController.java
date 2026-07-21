package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportPageResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportReviewRequest;
import com.typenull.pingdom.verification.application.ScoutFieldReportService;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/scout-field-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
@Validated
public class AdminScoutFieldReportController {

    private final ScoutFieldReportService service;

    @GetMapping
    @Operation(summary = "Scout 현장 제보 목록 조회")
    public ScoutFieldReportPageResponse list(
            @RequestParam(required = false) ScoutFieldReportStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.listForAdmin(admin.userId(), status, page, limit);
    }

    @PostMapping("/{reportId}/review")
    @Operation(summary = "Scout 현장 제보 심사")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현장 제보 심사 성공"),
            @ApiResponse(responseCode = "400", description = "심사 요청 검증 실패"),
            @ApiResponse(responseCode = "404", description = "현장 제보를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 심사된 현장 제보")
    })
    public ScoutFieldReportResponse review(
            @PathVariable Long reportId,
            @Valid @RequestBody ScoutFieldReportReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.review(admin.userId(), reportId, request);
    }
}
