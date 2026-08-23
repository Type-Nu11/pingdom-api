package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCancelRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeListResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeUpdateRequest;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingNoticeService;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}/operating-notices")
@RequiredArgsConstructor
@AuthenticatedOnly
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantPlaceOperatingNoticeController {

    private final PlaceOperatingNoticeService placeOperatingNoticeService;

    @GetMapping
    @Operation(summary = "점주 상점 운영 상태 공지 목록 조회", description = "점주 또는 해당 장소의 운영 공지 관리 권한이 있는 팀원이 전체 운영 상태 공지를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "운영 상태 공지 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = PlaceOperatingNoticeListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 장소 운영 공지를 조회할 권한이 없음",
                    content = @Content(schema = @Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = com.typenull.pingdom.shared.api.dto.ErrorResponse.class))
            )
    })
    public ResponseEntity<PlaceOperatingNoticeListResponse> listNotices(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.listByMerchant(userId(user), placeId));
    }

    @PostMapping
    @Operation(summary = "점주 상점 운영 상태 공지 생성", description = "점주가 본인 상점의 임시 운영 상태 공지를 생성합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> createNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Valid @RequestBody PlaceOperatingNoticeCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeOperatingNoticeService.createByMerchant(userId(user), placeId, request));
    }

    @PatchMapping("/{noticeId}")
    @Operation(summary = "점주 상점 운영 상태 공지 수정", description = "점주가 본인 상점의 운영 상태 공지 내용을 수정합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> updateNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "공지 ID", example = "10") @PathVariable Long noticeId,
            @Valid @RequestBody PlaceOperatingNoticeUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.updateByMerchant(userId(user), placeId, noticeId, request));
    }

    @PostMapping("/{noticeId}/cancel")
    @Operation(summary = "점주 상점 운영 상태 공지 취소", description = "점주가 본인 상점의 예약 또는 활성 운영 상태 공지를 취소합니다.")
    public ResponseEntity<PlaceOperatingNoticeResponse> cancelNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "공지 ID", example = "10") @PathVariable Long noticeId,
            @Valid @RequestBody PlaceOperatingNoticeCancelRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.cancelByMerchant(userId(user), placeId, noticeId, request));
    }

    private Long userId(JwtAuthenticatedUser user) {
        return JwtAuthenticatedUser.require(
                user,
                () -> new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_FORBIDDEN)
        ).userId();
    }
}
