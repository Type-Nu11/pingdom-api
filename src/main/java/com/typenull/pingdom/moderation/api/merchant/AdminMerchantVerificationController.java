package com.typenull.pingdom.moderation.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationReviewRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantVerificationAdminService;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchant-verifications")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMerchantVerificationController {

    private final MerchantVerificationAdminService verificationAdminService;

    @GetMapping
    @Operation(summary = "Merchant 검증 신청 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merchant 검증 신청 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "조회 조건 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminMerchantVerificationPageResponse list(
            @RequestParam(required = false) MerchantVerificationStatus identityStatus,
            @RequestParam(required = false) MerchantVerificationStatus businessStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return verificationAdminService.list(identityStatus, businessStatus, page, limit);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Merchant 검증 신청 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merchant 검증 신청 상세 조회 성공"),
            @ApiResponse(responseCode = "404", description = "Merchant 검증 신청을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminMerchantVerificationResponse get(
            @PathVariable Long userId,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return verificationAdminService.get(admin.userId(), userId);
    }

    @PostMapping("/{userId}/review")
    @Operation(summary = "Merchant 신원 및 사업자 수동 심사")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Merchant 검증 수동 심사 성공"),
            @ApiResponse(responseCode = "400", description = "심사 요청 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Merchant 프로필 또는 검증 신청을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검증 필수 조건 또는 현재 심사 상태가 유효하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AdminMerchantVerificationResponse review(
            @PathVariable Long userId,
            @Valid @RequestBody MerchantVerificationReviewRequest request,
            @CurrentUser JwtAuthenticatedUser admin
    ) {
        return verificationAdminService.review(admin.userId(), userId, request);
    }
}
