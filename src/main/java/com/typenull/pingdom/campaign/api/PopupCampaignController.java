package com.typenull.pingdom.campaign.api;

import com.typenull.pingdom.campaign.api.dto.PublicPopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PublicPopupCampaignResponse;
import com.typenull.pingdom.campaign.application.PopupCampaignQueryService;
import com.typenull.pingdom.campaign.domain.exception.CampaignErrorCode;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/popup-campaigns")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
/** 노출 가능한 팝업 캠페인을 조회하는 API 진입점입니다. */
public class PopupCampaignController {

    private final PopupCampaignQueryService queryService;

    @GetMapping
    @Operation(
            summary = "진행 중인 공개 팝업 캠페인 목록 조회",
            description = "PUBLISHED 상태이며 시작 시각 이상, 종료 시각 미만인 캠페인만 조회합니다. "
                    + "빈 목록은 items=[], totalCount=0, totalPages=0, hasNext=false로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 팝업 캠페인 목록 조회 성공", content = @Content(schema = @Schema(implementation = PublicPopupCampaignPageResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "페이지 또는 장소 필터 형식이 올바르지 않음 (INVALID_INPUT)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "INVALID_INPUT",
                                    value = "{\"message\":\"브랜드 또는 캠페인 입력값이 올바르지 않습니다.\",\"code\":\"INVALID_INPUT\"}"
                            )
                    )
            )
    })
    public PublicPopupCampaignPageResponse list(
            @io.swagger.v3.oas.annotations.Parameter(description = "장소 ID 필터", example = "101")
            @RequestParam(required = false) Long placeId,
            @io.swagger.v3.oas.annotations.Parameter(description = "페이지 번호. 1 미만은 1, 10000 초과는 10000으로 보정됩니다.", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "10000", defaultValue = "1"), example = "1")
            @RequestParam(defaultValue = "1") int page,
            @io.swagger.v3.oas.annotations.Parameter(description = "페이지 크기. 1 미만은 1, 100 초과는 100으로 보정됩니다.", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"), example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.list(placeId, page, limit);
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "진행 중인 공개 팝업 캠페인 상세 조회", description = "종료·비공개·없는 캠페인은 모두 404 CAMPAIGN_NOT_FOUND로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "공개 팝업 캠페인 상세 조회 성공", content = @Content(schema = @Schema(implementation = PublicPopupCampaignResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "캠페인 ID 형식이 올바르지 않음 (INVALID_INPUT)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "INVALID_INPUT",
                                    value = "{\"message\":\"브랜드 또는 캠페인 입력값이 올바르지 않습니다.\",\"code\":\"INVALID_INPUT\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "캠페인을 찾을 수 없거나 공개 대상이 아님 (CAMPAIGN_NOT_FOUND)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "CAMPAIGN_NOT_FOUND",
                                    value = "{\"message\":\"팝업 캠페인을 찾을 수 없습니다.\",\"code\":\"CAMPAIGN_NOT_FOUND\"}"
                            )
                    )
            )
    })
    public PublicPopupCampaignResponse get(
            @io.swagger.v3.oas.annotations.Parameter(description = "팝업 캠페인 ID", example = "1") @PathVariable Long campaignId
    ) {
        return queryService.get(campaignId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch() {
        return ResponseEntity.badRequest().body(ErrorResponse.from(CampaignErrorCode.INVALID_INPUT));
    }
}
