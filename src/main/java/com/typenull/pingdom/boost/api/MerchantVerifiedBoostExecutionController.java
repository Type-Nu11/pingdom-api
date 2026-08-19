package com.typenull.pingdom.boost.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionStartRequest;
import com.typenull.pingdom.boost.application.VerifiedBoostExecutionService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/verified-boost-executions")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantVerifiedBoostExecutionController {

    private final VerifiedBoostExecutionService service;

    @PostMapping
    @Operation(summary = "Verified Boost 집행 시작")
    public ResponseEntity<VerifiedBoostExecutionResponse> start(
            @Valid @RequestBody VerifiedBoostExecutionStartRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(user.userId(), request));
    }

    @PostMapping("/{executionId}/stop")
    @Operation(summary = "Verified Boost 집행 중단")
    public VerifiedBoostExecutionResponse stop(@PathVariable Long executionId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.stop(user.userId(), executionId);
    }

    @GetMapping
    @Operation(summary = "내 Verified Boost 집행 목록 조회")
    public VerifiedBoostExecutionPageResponse list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.list(user.userId(), page, limit);
    }
}
