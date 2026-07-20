package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.LocationCheckInService;
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
@RequestMapping("/location-check-ins")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
public class LocationCheckInController {
    private final LocationCheckInService service;

    @PostMapping
    @Operation(summary = "클라이언트 위치 기반 방문 체크인",
            description = "클라이언트가 제출한 위치와 장소 좌표의 근접성을 확인하며 실제 GPS를 증명하지 않습니다.")
    @ApiResponse(responseCode = "201", description = "위치 근접성 확인 기록 생성")
    public ResponseEntity<LocationCheckInResponse> checkIn(@Valid @RequestBody LocationCheckInRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkIn(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 위치 체크인 목록 조회")
    public LocationCheckInPageResponse listMine(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }
}
