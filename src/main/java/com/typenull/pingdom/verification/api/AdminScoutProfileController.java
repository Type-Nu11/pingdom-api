package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityGrantRequest;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityReviewRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfilePageResponse;
import com.typenull.pingdom.verification.api.dto.ScoutProfileResponse;
import com.typenull.pingdom.verification.api.dto.ScoutProfileReviewRequest;
import com.typenull.pingdom.verification.application.ScoutProfileService;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
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
@RequestMapping("/admin/scout-profiles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Web", description = "웹(관리자) 전용 API")
@Validated
public class AdminScoutProfileController {

    private final ScoutProfileService service;

    @GetMapping
    @Operation(summary = "Scout 프로필 목록 조회")
    public ScoutProfilePageResponse list(
            @RequestParam(required = false) ScoutProfileStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.listForAdmin(admin.userId(), status, page, limit);
    }

    @GetMapping("/{scoutUserId}")
    @Operation(summary = "Scout 프로필 및 활동 자격 상세 조회")
    public ScoutProfileResponse get(
            @PathVariable Long scoutUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.getForAdmin(admin.userId(), scoutUserId);
    }

    @PostMapping("/{scoutUserId}/approve")
    @Operation(summary = "Scout 프로필 승인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scout 프로필 승인 성공"),
            @ApiResponse(responseCode = "403", description = "Scout 심사 권한 없음"),
            @ApiResponse(responseCode = "409", description = "현재 프로필 상태에서 승인 불가")
    })
    public ScoutProfileResponse approve(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutProfileReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.approveProfile(admin.userId(), scoutUserId, request);
    }

    @PostMapping("/{scoutUserId}/suspend")
    @Operation(summary = "Scout 프로필 정지")
    public ScoutProfileResponse suspend(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutProfileReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.suspendProfile(admin.userId(), scoutUserId, request);
    }

    @PostMapping("/{scoutUserId}/revoke")
    @Operation(summary = "Scout 프로필 회수")
    public ScoutProfileResponse revoke(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutProfileReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.revokeProfile(admin.userId(), scoutUserId, request);
    }

    @PostMapping("/{scoutUserId}/eligibility/grant")
    @Operation(summary = "Scout 활동 자격 부여")
    public ScoutProfileResponse grantEligibility(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutActivityEligibilityGrantRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.grantEligibility(admin.userId(), scoutUserId, request);
    }

    @PostMapping("/{scoutUserId}/eligibility/suspend")
    @Operation(summary = "Scout 활동 자격 정지")
    public ScoutProfileResponse suspendEligibility(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutActivityEligibilityReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.suspendEligibility(admin.userId(), scoutUserId, request);
    }

    @PostMapping("/{scoutUserId}/eligibility/revoke")
    @Operation(summary = "Scout 활동 자격 회수")
    public ScoutProfileResponse revokeEligibility(
            @PathVariable Long scoutUserId,
            @Valid @RequestBody ScoutActivityEligibilityReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.revokeEligibility(admin.userId(), scoutUserId, request);
    }
}
