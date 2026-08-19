package com.typenull.pingdom.offer.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
import com.typenull.pingdom.offer.application.TouristOfferService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/offers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class TouristOfferController {

    private final TouristOfferService offerService;

    @GetMapping
    @Operation(summary = "발급 가능한 관광객 전용 Offer 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 목록 조회 성공", content = @Content(schema = @Schema(implementation = OfferPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferPageResponse list(
            @RequestParam(required = false) Long placeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return offerService.list(placeId, page, limit);
    }

    @GetMapping("/{offerId}")
    @Operation(summary = "발급 가능한 관광객 전용 Offer 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 상세 조회 성공", content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "발급 가능한 Offer를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferResponse get(@PathVariable Long offerId) {
        return offerService.get(offerId);
    }

    @PostMapping("/{offerId}/coupons")
    @Operation(summary = "관광객 Coupon 발급", description = "진행 중인 여행 일정이 있는 일반 사용자에게 Offer당 한 번만 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon 발급 성공", content = @Content(schema = @Schema(implementation = CouponResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관광객 발급 조건 불충족", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offer를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "중복 발급, 발급 기간 종료 또는 수량 소진", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CouponResponse> issue(
            @PathVariable Long offerId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(offerService.issue(user.userId(), offerId));
    }
}
