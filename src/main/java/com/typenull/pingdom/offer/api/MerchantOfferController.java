package com.typenull.pingdom.offer.api;

import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
import com.typenull.pingdom.offer.application.MerchantOfferService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/merchant-owner/offers")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantOfferController {

    private final MerchantOfferService offerService;

    @PostMapping
    @Operation(summary = "관광객 전용 Offer 초안 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Offer 초안 등록 성공", content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 기간 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "장소 소유 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OfferResponse> create(
            @Valid @RequestBody OfferCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(offerService.create(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 관광객 전용 Offer 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 목록 조회 성공", content = @Content(schema = @Schema(implementation = OfferPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.list(user.userId(), page, limit);
    }

    @GetMapping("/{offerId}")
    @Operation(summary = "내 관광객 전용 Offer 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 상세 조회 성공", content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offer를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferResponse get(
            @PathVariable Long offerId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.get(user.userId(), offerId);
    }

    @PostMapping("/{offerId}/publish")
    @Operation(summary = "관광객 전용 Offer 게시")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 게시 성공", content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offer를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "게시할 수 없는 상태 또는 기간", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferResponse publish(
            @PathVariable Long offerId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.publish(user.userId(), offerId);
    }

    @PostMapping("/{offerId}/close")
    @Operation(summary = "관광객 전용 Offer 종료")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Offer 종료 성공", content = @Content(schema = @Schema(implementation = OfferResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Offer를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "종료할 수 없는 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OfferResponse close(
            @PathVariable Long offerId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.close(user.userId(), offerId);
    }

    @PostMapping("/coupons/redeem")
    @Operation(summary = "관광객 Coupon 사용 처리", description = "본인이 소유한 장소의 Offer에서 발급된 Coupon만 사용할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon 사용 처리 성공", content = @Content(schema = @Schema(implementation = CouponResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Coupon을 찾을 수 없거나 소유 장소의 Coupon이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "사용되었거나 만료된 Coupon", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CouponResponse redeem(
            @Valid @RequestBody CouponRedeemRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.redeem(user.userId(), request);
    }
}
