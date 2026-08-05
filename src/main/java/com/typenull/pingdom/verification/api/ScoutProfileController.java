package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.verification.api.dto.ScoutProfileRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileResponse;
import com.typenull.pingdom.verification.application.ScoutProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/scout-profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class ScoutProfileController {

    private final ScoutProfileService service;

    @PostMapping
    @Operation(summary = "Scout 프로필 신청")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Scout 프로필 신청 성공"),
            @ApiResponse(responseCode = "403", description = "활성 일반 사용자만 신청 가능"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 Scout 프로필")
    })
    public ResponseEntity<ScoutProfileResponse> apply(
            @Valid @RequestBody ScoutProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 Scout 프로필 및 활동 자격 조회")
    public ScoutProfileResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.get(user.userId());
    }

    @PutMapping
    @Operation(summary = "내 Scout 프로필 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scout 프로필 수정 성공"),
            @ApiResponse(responseCode = "409", description = "현재 프로필 상태에서 수정 불가")
    })
    public ScoutProfileResponse update(
            @Valid @RequestBody ScoutProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.update(user.userId(), request);
    }
}
