package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotQuery;
import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotResponse;
import com.typenull.pingdom.place.application.service.localhot.PlaceLocalHotQueryService;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/places/local-hot")
@RequiredArgsConstructor
@Tag(name = "App", description = "앱 전용 API")
public class PlaceLocalHotController {

    private final PlaceLocalHotQueryService placeLocalHotQueryService;

    @GetMapping
    @Operation(
            summary = "시·군·구 우리 지역 핫플 조회",
            description = "좌표 또는 이전 응답의 regionCode 중 하나로 시·군·구의 공개 운영 장소를 현재 북마크 수 순으로 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지역 핫플 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "좌표·지역 코드·페이지 조건이 올바르지 않음", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "판정되거나 등록된 시·군·구를 찾을 수 없음", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "502", description = "외부 지역 판정 서비스 호출 실패", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "503", description = "외부 지역 판정 서비스 미설정 또는 사용 불가", useReturnTypeSchema = true)
    })
    public ResponseEntity<PlaceLocalHotResponse> findLocalHotPlaces(
            @Parameter(description = "현재 위도. longitude와 함께 전달하며 regionCode와 동시에 전달할 수 없습니다.", example = "37.5172")
            @DecimalMin("-90") @DecimalMax("90") @RequestParam(required = false) Double latitude,
            @Parameter(description = "현재 경도. latitude와 함께 전달하며 regionCode와 동시에 전달할 수 없습니다.", example = "127.0473")
            @DecimalMin("-180") @DecimalMax("180") @RequestParam(required = false) Double longitude,
            @Parameter(description = "이전 좌표 조회 응답의 5자리 시·군·구 코드", example = "11680")
            @Pattern(regexp = "\\d{5}") @RequestParam(required = false) String regionCode,
            @Parameter(description = "페이지 번호", example = "1")
            @Min(1) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기. 최대 50", example = "20")
            @Min(1) @Max(50) @RequestParam(defaultValue = "20") int limit,
            @CurrentUser JwtAuthenticatedUser user
    ) {
        return ResponseEntity.ok(placeLocalHotQueryService.find(
                new PlaceLocalHotQuery(latitude, longitude, regionCode, page, limit),
                user.userId()
        ));
    }
}
