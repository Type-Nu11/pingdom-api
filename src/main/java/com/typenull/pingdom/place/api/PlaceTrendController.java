package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.trend.PlaceTrendPeriod;
import com.typenull.pingdom.place.api.dto.trend.PlaceTrendResponse;
import com.typenull.pingdom.place.application.service.place.PlaceTrendQueryService;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/places/trends")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PlaceTrendController {

    private final PlaceTrendQueryService placeTrendQueryService;

    @GetMapping
    @Operation(summary = "전국 장소 트렌드 조회", description = "이력 수집 시작 시점부터 최근 7일의 북마크 순증가량으로 공개 운영 장소를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전국 장소 트렌드 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 기간 또는 페이지 조건", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", useReturnTypeSchema = true)
    })
    public ResponseEntity<PlaceTrendResponse> findTrends(
            @Parameter(description = "지원 기간. 현재 WEEK만 지원합니다.", example = "WEEK")
            @RequestParam(defaultValue = "WEEK") String period,
            @Parameter(description = "페이지 번호", example = "1")
            @Min(1) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기. 최대 50", example = "20")
            @Min(1) @Max(50) @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeTrendQueryService.find(PlaceTrendPeriod.from(period), page, limit, user.userId()));
    }
}
