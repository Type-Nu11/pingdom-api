package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.place.information.reverification.*;
import com.typenull.pingdom.place.application.service.place.information.PlaceInformationReverificationService;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/merchant-owner/place-information-reverification-requests")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
@Tag(name = "App Place", description = "앱용 장소 API")
public class MerchantPlaceInformationReverificationController {

    private final PlaceInformationReverificationService service;

    @GetMapping
    @Operation(summary = "내 장소 정보 재확인 요청 목록 조회")
    public PlaceInformationReverificationListResponse listMine(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.listMine(userId(user), page, limit);
    }

    @PostMapping("/{requestId}/responses")
    @Operation(summary = "장소 정보 재확인 응답 제출")
    public PlaceInformationReverificationResponse respond(
            @PathVariable Long requestId,
            @Valid @RequestBody PlaceInformationReverificationResponseRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return service.respond(userId(user), requestId, request);
    }

    private Long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(user,
                () -> new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_FORBIDDEN)).userId();
    }
}
