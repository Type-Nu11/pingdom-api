package com.typenull.pingdom.moderation.api.place.information;

import com.typenull.pingdom.place.api.dto.place.information.reverification.*;
import com.typenull.pingdom.place.application.service.place.information.PlaceInformationReverificationService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/admin/places/{placeId}/information-reverification-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPlaceInformationReverificationController {

    private final PlaceInformationReverificationService service;

    @PostMapping
    @Operation(summary = "장소 정보 재확인 요청 생성")
    public ResponseEntity<PlaceInformationReverificationResponse> create(
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceInformationReverificationCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId(admin), placeId, request));
    }

    @GetMapping
    @Operation(summary = "장소 정보 재확인 요청 목록 조회")
    public PlaceInformationReverificationListResponse list(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return service.listByPlace(placeId, page, limit);
    }

    @PostMapping("/{requestId}/reminders")
    @Operation(summary = "장소 정보 재확인 리마인드 발행")
    public PlaceInformationReverificationResponse remind(
            @PathVariable Long placeId, @PathVariable Long requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.remind(userId(admin), placeId, requestId);
    }

    @PostMapping("/{requestId}/complete")
    @Operation(summary = "장소 정보 재확인 요청 완료")
    public PlaceInformationReverificationResponse complete(
            @PathVariable Long placeId, @PathVariable Long requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.complete(userId(admin), placeId, requestId);
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "장소 정보 재확인 요청 취소")
    public PlaceInformationReverificationResponse cancel(
            @PathVariable Long placeId, @PathVariable Long requestId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser admin
    ) {
        return service.cancel(userId(admin), placeId, requestId);
    }

    private Long userId(JwtAuthenticatedUser user) {
        return user == null ? null : user.userId();
    }
}
