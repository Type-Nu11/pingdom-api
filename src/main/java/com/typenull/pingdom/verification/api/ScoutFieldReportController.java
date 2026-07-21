package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.MyScoutFieldReportPageResponse;
import com.typenull.pingdom.verification.api.dto.MyScoutFieldReportResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportCreateRequest;
import com.typenull.pingdom.verification.application.ScoutFieldReportService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/scout-field-reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@Validated
public class ScoutFieldReportController {

    private final ScoutFieldReportService service;

    @PostMapping
    @Operation(summary = "Scout 현장 제보 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "현장 제보 생성 성공"),
            @ApiResponse(responseCode = "400", description = "제보 내용 검증 실패"),
            @ApiResponse(responseCode = "403", description = "활성 Scout 계정이 아님"),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "처리 중인 동일 제보 존재")
    })
    public ResponseEntity<MyScoutFieldReportResponse> submit(
            @Valid @RequestBody ScoutFieldReportCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Scout 현장 제보 목록 조회")
    public MyScoutFieldReportPageResponse listMine(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "내 Scout 현장 제보 상세 조회")
    public MyScoutFieldReportResponse getMine(
            @PathVariable Long reportId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.getMine(user.userId(), reportId);
    }
}
