package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse;
import com.typenull.pingdom.place.application.service.review.PlaceReviewSummaryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places/{placeId}/reviews/summary")
@RequiredArgsConstructor
@AuthenticatedOnly
@ApiAudience(ApiAudience.Group.COMMON)
@Tag(name = SwaggerTagCatalog.PLACE_REVIEW)
@SecurityRequirement(name = "bearerAuth")
public class PlaceReviewSummaryController {

    private final PlaceReviewSummaryService service;

    @GetMapping
    @Operation(summary = "장소 리뷰 전체 추천 태그 집계 조회", description = """
            일반 리뷰 목록과 동일하게 인증이 필요하며 VISIBLE 리뷰만 집계합니다. 페이지·limit·요청 body는 없습니다.
            totalReviewCount는 전체 공개 리뷰 건수, participantCount와 태그 count는 유효 태그를 선택한 고유 작성자 수입니다.
            한 작성자의 여러 공개 리뷰는 태그 합집합으로 계산하며 최신 리뷰만 고르는 정책은 사용하지 않습니다.
            복수 recommendReasons가 있으면 단일 recommendReason을 무시하고, 비어 있을 때만 정확히 일치하는 지원 단일 코드를 사용합니다.
            알 수 없는 태그와 작성자가 없는 값은 참여·태그 인원에서 제외하지만 해당 공개 리뷰는 전체 리뷰 건수에 포함합니다.
            7개 태그를 FRIENDLY, EASY_TO_FIND, GOOD_FOOD, MULTILINGUAL_SUPPORT, PARKING, PHOTO_SPOT, CLEAN 순서로 0건까지 반환합니다.
            캐시 없이 하나의 SQL 시점에서 조회하므로 작성·수정·삭제·숨김·복구가 커밋된 뒤 다음 조회에 반영됩니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체 집계. 리뷰가 없는 장소는 건수·인원·모든 태그가 0입니다.",
                    content = @Content(schema = @Schema(implementation = PlaceReviewSummaryResponse.class),
                            examples = @ExampleObject(value = """
                                    {"placeId":17,"totalReviewCount":3,"participantCount":2,"reasonCounts":[
                                    {"reason":"FRIENDLY","count":1},{"reason":"EASY_TO_FIND","count":0},
                                    {"reason":"GOOD_FOOD","count":1},{"reason":"MULTILINGUAL_SUPPORT","count":0},
                                    {"reason":"PARKING","count":0},{"reason":"PHOTO_SPOT","count":2},{"reason":"CLEAN","count":1}]}
                                    """))),
            @ApiResponse(responseCode = "400", description = "숫자가 아니거나 Long 범위를 벗어난 장소 ID: INVALID_REQUEST_PARAMETER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 없음·잘못된 토큰: INVALID_TOKEN / 만료 토큰: EXPIRED_TOKEN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 장소(0·음수 포함): PLACE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "집계 조회 실패: INTERNAL_SERVER_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PlaceReviewSummaryResponse summary(
            @Parameter(description = "장소 ID. 존재하지 않는 ID는 404로 응답합니다.", example = "17")
            @PathVariable Long placeId
    ) {
        return service.summary(placeId);
    }
}
