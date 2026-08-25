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
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(summary = "점주 상점 운영 상태 공지 생성", description = "OWNER만 본인 장소에 공지를 생성할 수 있습니다. startsAt이 현재 시각 이후이면 SCHEDULED, 그렇지 않으면 ACTIVE로 생성되며, 같은 장소·공지 유형에는 활성 또는 예약 공지를 하나만 둘 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "운영 상태 공지 생성 성공", content = @Content(schema = @Schema(implementation = PlaceOperatingNoticeResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 공지 상태가 올바르지 않음", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}), examples = @ExampleObject(name = "PLACE_OPERATING_NOTICE_INVALID_REQUEST", value = "{\"message\":\"상점 운영 상태 공지 요청이 올바르지 않습니다.\",\"code\":\"PLACE_OPERATING_NOTICE_INVALID_REQUEST\"}"))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "INVALID_TOKEN", value = "{\"message\":\"유효하지 않은 토큰입니다.\",\"code\":\"INVALID_TOKEN\"}"))),
            @ApiResponse(responseCode = "403", description = "장소 운영 공지 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "PLACE_OPERATING_NOTICE_FORBIDDEN", value = "{\"message\":\"해당 상점 운영 상태 공지를 관리할 권한이 없습니다.\",\"code\":\"PLACE_OPERATING_NOTICE_FORBIDDEN\"}"))),
            @ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "PLACE_NOT_FOUND", value = "{\"message\":\"장소를 찾을 수 없습니다.\",\"code\":\"PLACE_NOT_FOUND\"}"))),
            @ApiResponse(responseCode = "409", description = "같은 공지 유형의 활성 또는 예약 공지가 이미 존재함", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "PLACE_OPERATING_NOTICE_ALREADY_ACTIVE", value = "{\"message\":\"이미 활성 또는 예약된 상점 운영 상태 공지가 있습니다.\",\"code\":\"PLACE_OPERATING_NOTICE_ALREADY_ACTIVE\"}")))
    })
    public ResponseEntity<PlaceOperatingNoticeResponse> createNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Valid @RequestBody PlaceOperatingNoticeCreateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(placeOperatingNoticeService.createByMerchant(userId(user), placeId, request));
    }

    @PatchMapping("/{noticeId}")
    @Operation(summary = "점주 상점 운영 상태 공지 수정", description = "OWNER만 본인 장소의 SCHEDULED 또는 ACTIVE 공지의 심각도와 메시지를 수정할 수 있습니다. 생성·수정·취소 결과는 공개 운영 공지 조회와 동일한 공지 리소스를 변경합니다. 잘못된 상태는 PLACE_OPERATING_NOTICE_INVALID_REQUEST, 권한 부족은 PLACE_OPERATING_NOTICE_FORBIDDEN, 대상 없음은 PLACE_OPERATING_NOTICE_NOT_FOUND로 응답합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "운영 상태 공지 수정 성공", content = @Content(schema = @Schema(implementation = PlaceOperatingNoticeResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 수정 가능하지 않은 공지 상태", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "장소 운영 공지 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소 또는 공지를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PlaceOperatingNoticeResponse> updateNotice(
            @Parameter(description = "장소 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "공지 ID", example = "10") @PathVariable Long noticeId,
            @Valid @RequestBody PlaceOperatingNoticeUpdateRequest request,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeOperatingNoticeService.updateByMerchant(userId(user), placeId, noticeId, request));
    }

    @PostMapping("/{noticeId}/cancel")
    @Operation(summary = "점주 상점 운영 상태 공지 취소", description = "OWNER만 본인 장소의 SCHEDULED 또는 ACTIVE 공지를 취소할 수 있습니다. 이미 취소되었거나 종료된 공지는 400 PLACE_OPERATING_NOTICE_INVALID_REQUEST로 응답하며, 권한 부족과 대상 없음은 각각 PLACE_OPERATING_NOTICE_FORBIDDEN, PLACE_OPERATING_NOTICE_NOT_FOUND로 구분합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "운영 상태 공지 취소 성공", content = @Content(schema = @Schema(implementation = PlaceOperatingNoticeResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 또는 취소 가능하지 않은 공지 상태", content = @Content(schema = @Schema(oneOf = {ErrorResponse.class, ValidationErrorResponse.class}), examples = @ExampleObject(name = "PLACE_OPERATING_NOTICE_INVALID_REQUEST", value = "{\"message\":\"상점 운영 상태 공지 요청이 올바르지 않습니다.\",\"code\":\"PLACE_OPERATING_NOTICE_INVALID_REQUEST\"}"))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "장소 운영 공지 관리 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장소 또는 공지를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
