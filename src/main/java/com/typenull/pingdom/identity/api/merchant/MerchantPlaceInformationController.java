package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceInformationUpdateRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceInformationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}/information")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MerchantPlaceInformationController {

    private final MerchantPlaceInformationService informationService;

    @GetMapping
    @Operation(summary = "Merchant 장소 정보 조회", description = "장소 관리자 또는 소유자만 본인 장소의 Merchant 관리 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = MerchantPlaceInformationResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "장소 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소 정보를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MerchantPlaceInformationResponse get(
            @PathVariable Long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return informationService.get(user.userId(), placeId);
    }

    @PutMapping
    @Operation(summary = "Merchant 장소 정보 등록 및 수정", description = "장소 관리자 또는 소유자가 장소 소개와 방문 전환 링크를 등록하거나 전체 교체합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(schema = @Schema(implementation = MerchantPlaceInformationResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "장소 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MerchantPlaceInformationResponse upsert(
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantPlaceInformationUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return informationService.upsert(user.userId(), placeId, request);
    }
}
