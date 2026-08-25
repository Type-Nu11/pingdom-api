package com.typenull.pingdom.payment.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.payment.api.dto.*;
import com.typenull.pingdom.payment.application.*;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
@org.springframework.validation.annotation.Validated
/** 결제 내역과 결제 상태 조회 API의 진입점입니다. */
public class PaymentController {
    private final PaymentQueryService queryService;

    @GetMapping
    @Operation(
            summary = "내 결제 목록 조회",
            description = "결제 내역이 없으면 payments가 빈 배열인 200 응답을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 결제 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PaymentPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "페이지 요청 값 검증 실패 (VALIDATION_FAILED)",
                    content = @Content(
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "VALIDATION_FAILED",
                                    value = """
                                            {"message":"입력값을 확인해주세요.","code":"VALIDATION_FAILED","errors":{"page":"1 이상이어야 합니다."}}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아님 (PAYMENT_FORBIDDEN)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PAYMENT_FORBIDDEN",
                                    value = """
                                            {"message":"이 결제 거래를 처리할 권한이 없습니다.","code":"PAYMENT_FORBIDDEN"}
                                            """
                            )
                    )
            )
    })
    public PaymentPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.listMine(user.userId(), page, limit);
    }

    @GetMapping("/{paymentId}")
    @Operation(
            summary = "내 결제 상세 조회",
            description = "본인 결제만 조회할 수 있으며, 실패·환불 상태별 nullable 필드는 응답에 포함됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 결제 상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 토큰",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_TOKEN",
                                            value = """
                                                    {"message":"유효하지 않은 토큰입니다.","code":"INVALID_TOKEN"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "EXPIRED_TOKEN",
                                            value = """
                                                    {"message":"만료된 토큰입니다.","code":"EXPIRED_TOKEN"}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 일반 사용자 계정이 아니거나 다른 사용자의 결제",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PAYMENT_FORBIDDEN",
                                    value = """
                                            {"message":"이 결제 거래를 처리할 권한이 없습니다.","code":"PAYMENT_FORBIDDEN"}
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "결제 거래를 찾을 수 없음 (PAYMENT_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PAYMENT_NOT_FOUND",
                                    value = """
                                            {"message":"결제 거래를 찾을 수 없습니다.","code":"PAYMENT_NOT_FOUND"}
                                            """
                            )
                    )
            )
    })
    public PaymentResponse get(@PathVariable Long paymentId,
            @CurrentUser JwtAuthenticatedUser user) {
        return queryService.getMine(user.userId(), paymentId);
    }
}
