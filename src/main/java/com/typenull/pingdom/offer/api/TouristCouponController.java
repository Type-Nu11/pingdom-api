package com.typenull.pingdom.offer.api;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.offer.api.dto.CouponPageResponse;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.application.TouristOfferService;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
/** 관광 쿠폰 발급·조회·사용 요청의 API 진입점입니다. */
public class TouristCouponController {

    private final TouristOfferService offerService;

    @GetMapping
    @Operation(summary = "내 관광객 Coupon 목록 조회", description = "Offer 제목·혜택과 장소 정보는 Coupon 발급 시점 스냅샷을 반환하므로 Offer 종료 또는 이후 정보 변경과 무관하게 유지됩니다. 원본 정보를 복구할 수 없으면 해당 필드는 null입니다. 발급일 기간은 양 끝값을 포함합니다. status=ISSUED는 아직 만료되지 않은 쿠폰, EXPIRED는 발급 상태이면서 expiresAt이 현재 시각 이하인 쿠폰입니다. 기본 정렬은 issuedAt 내림차순, id 내림차순이며 page와 limit은 각각 최소 1, limit 최대 100으로 보정됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon 목록 조회 성공", content = @Content(schema = @Schema(implementation = CouponPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "발급일 기간 조건 또는 요청 파라미터 형식이 올바르지 않음", content = @Content(
                    schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}),
                    examples = {
                            @ExampleObject(name = "COUPON_LIST_FILTER_INVALID", value = "{\"message\":\"쿠폰 조회 기간 조건이 올바르지 않습니다.\",\"code\":\"COUPON_LIST_FILTER_INVALID\"}"),
                            @ExampleObject(name = "VALIDATION_FAILED", value = "{\"message\":\"입력값이 올바르지 않습니다.\",\"code\":\"VALIDATION_FAILED\",\"errors\":{\"issuedFrom\":\"날짜 형식이 올바르지 않습니다.\"}}")
                    }
            )),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "INVALID_TOKEN", value = "{\"message\":\"유효하지 않은 토큰입니다.\",\"code\":\"INVALID_TOKEN\"}")
            ))
    })
    public CouponPageResponse list(
            @Parameter(description = "쿠폰 상태. 생략하면 모든 상태를 조회합니다.", example = "ISSUED") @RequestParam(required = false) CouponStatus status,
            @Parameter(description = "발급일 조회 시작 시각(포함, ISO-8601 local date-time)", example = "2026-08-01T00:00:00") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime issuedFrom,
            @Parameter(description = "발급일 조회 종료 시각(포함, ISO-8601 local date-time)", example = "2026-08-31T23:59:59") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime issuedTo,
            @Parameter(description = "1부터 시작하는 페이지 번호", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기. 1~100 범위로 보정됩니다.", example = "20") @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.listCoupons(user.userId(), status, issuedFrom, issuedTo, page, limit);
    }

    @GetMapping("/{couponId}")
    @Operation(summary = "내 관광객 Coupon 단건 조회", description = "본인이 발급받은 Coupon의 현재 상태를 조회합니다. 만료 시각이 지난 미사용 Coupon은 EXPIRED로 반환하며, REDEEMED Coupon은 redeemedAt을 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon 단건 조회 성공", content = @Content(schema = @Schema(implementation = CouponResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "INVALID_TOKEN", value = "{\"message\":\"유효하지 않은 토큰입니다.\",\"code\":\"INVALID_TOKEN\"}")
            )),
            @ApiResponse(responseCode = "404", description = "Coupon을 찾을 수 없거나 요청자의 Coupon이 아님", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "COUPON_NOT_FOUND", value = "{\"message\":\"쿠폰을 찾을 수 없습니다.\",\"code\":\"COUPON_NOT_FOUND\"}")
            ))
    })
    public CouponResponse get(
            @Parameter(description = "Coupon ID", example = "1") @PathVariable Long couponId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return offerService.getCoupon(user.userId(), couponId);
    }
}
