package com.typenull.pingdom.reservation.api;

import com.typenull.pingdom.reservation.api.dto.ReservationQuoteResponse;
import com.typenull.pingdom.reservation.application.ReservationQuoteService;
import com.typenull.pingdom.shared.config.swagger.*;
import com.typenull.pingdom.shared.security.annotation.*;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/places/{placeId}/availabilities/{availabilityId}/quote")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.RESERVATION)
@Validated
public class ReservationQuoteController {
    private final ReservationQuoteService service;

    @GetMapping
    @Operation(summary = "예약 전 견적·취소 조건 조회", description = "활성 일반 사용자 본인의 확인 토큰을 준비합니다. 예약 생성·결제·재고 점유는 수행하지 않습니다. 유효기간은 최대 5분이며 슬롯 시작 전까지입니다. 사용자 확인 직전에 다시 조회하세요.")
    public ReservationQuoteResponse quote(@PathVariable Long placeId, @PathVariable Long availabilityId,
            @RequestParam @Min(1) int quantity, @CurrentUser JwtAuthenticatedUser user) {
        return service.issue(user.userId(), placeId, availabilityId, quantity);
    }
}
