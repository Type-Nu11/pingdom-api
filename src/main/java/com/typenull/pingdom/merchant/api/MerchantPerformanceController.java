package com.typenull.pingdom.merchant.api;

import com.typenull.pingdom.merchant.api.dto.MerchantPerformanceResponse;
import com.typenull.pingdom.merchant.application.MerchantPerformanceQueryService;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/performance")
@RequiredArgsConstructor
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantPerformanceController {
    private final MerchantPerformanceQueryService queryService;

    @GetMapping
    @Operation(
            summary = "Merchant 성과 요약 조회",
            description = "소유 장소의 추천 노출, 클릭, 북마크, 예약 전환 성과를 조회합니다."
    )
    public MerchantPerformanceResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return queryService.get(user.userId());
    }
}
