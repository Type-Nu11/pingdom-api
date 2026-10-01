package com.typenull.pingdom.availability.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.availability.api.dto.*;
import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant-owner/availabilities")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.MERCHANT)
@Tag(name = SwaggerTagCatalog.AVAILABILITY)
public class MerchantAvailabilityController {
    private final PlaceAvailabilityService service;

    @PutMapping("/{availabilityId}/reservation-terms")
    @Operation(summary = "예약 가격·취소 조건 설정", description = "가격은 통화의 최소 단위 정수입니다. 추가 비용은 예약당 한 번 적용합니다. 취소 정책은 명시적 취소 불가 또는 기한 내 수수료 0·전액 환불입니다. 기존 예약의 수락 조건은 변경하지 않습니다.")
    public ReservationTermsResponse updateTerms(@PathVariable Long availabilityId,
            @Valid @RequestBody ReservationTermsRequest request, @CurrentUser JwtAuthenticatedUser user) {
        return service.updateReservationTerms(user.userId(), availabilityId, request);
    }

    @PostMapping
    @Operation(summary = "예약 가능 시간 등록")
    @ApiResponse(responseCode = "201", description = "예약 가능 시간 등록 성공")
    public ResponseEntity<AvailabilityResponse> create(@Valid @RequestBody AvailabilityUpsertRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.userId(), request));
    }

    @PutMapping("/{availabilityId}")
    @Operation(summary = "예약 가능 시간 수정")
    public AvailabilityResponse update(@PathVariable Long availabilityId,
            @Valid @RequestBody AvailabilityUpsertRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.update(user.userId(), availabilityId, request);
    }

    @GetMapping
    @Operation(summary = "내 예약 가능 시간 목록 조회")
    public List<AvailabilityResponse> list(
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listOwned(user.userId());
    }

    @PostMapping("/{availabilityId}/activate")
    @Operation(summary = "예약 가능 시간 활성화")
    public AvailabilityResponse activate(@PathVariable Long availabilityId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), availabilityId, true);
    }

    @PostMapping("/{availabilityId}/deactivate")
    @Operation(summary = "예약 가능 시간 비활성화")
    public AvailabilityResponse deactivate(@PathVariable Long availabilityId,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.changeStatus(user.userId(), availabilityId, false);
    }
}
