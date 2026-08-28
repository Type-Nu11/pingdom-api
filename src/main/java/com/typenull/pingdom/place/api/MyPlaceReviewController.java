package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.review.MyPlaceReviewPageResponse;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/reviews")
@RequiredArgsConstructor
@Validated
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App", description = "앱 전용 API")
public class MyPlaceReviewController {

    private final PlaceReviewService placeReviewService;

    @GetMapping
    @Operation(summary = "내가 작성한 장소 리뷰 목록 조회", description = "삭제 완료된 리뷰는 목록과 총 개수에서 제외합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 리뷰 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPlaceReviewPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "페이지 요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 토큰",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public MyPlaceReviewPageResponse listMine(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return placeReviewService.listMine(user.userId(), page, limit);
    }
}
