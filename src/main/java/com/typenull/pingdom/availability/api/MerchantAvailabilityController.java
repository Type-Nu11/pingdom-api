package com.typenull.pingdom.availability.api;

import com.typenull.pingdom.availability.api.dto.*;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/availabilities")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantAvailabilityController {
    private final PlaceAvailabilityService service;

    @PostMapping
    @Operation(summary = "예약 가능 시간 등록")
    public ResponseEntity<AvailabilityResponse> create(@Valid @RequestBody AvailabilityUpsertRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @PutMapping("/{availabilityId}")
    @Operation(summary = "예약 가능 시간 수정")
    public AvailabilityResponse update(@PathVariable Long availabilityId,
            @Valid @RequestBody AvailabilityUpsertRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.update(user.userId(), availabilityId, request);
    }

    @GetMapping
    @Operation(summary = "내 예약 가능 시간 목록 조회")
    public List<AvailabilityResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.listOwned(user.userId());
    }

    @PostMapping("/{availabilityId}/activate")
    @Operation(summary = "예약 가능 시간 활성화")
    public AvailabilityResponse activate(@PathVariable Long availabilityId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), availabilityId, true);
    }

    @PostMapping("/{availabilityId}/deactivate")
    @Operation(summary = "예약 가능 시간 비활성화")
    public AvailabilityResponse deactivate(@PathVariable Long availabilityId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), availabilityId, false);
    }
}
