package com.typenull.pingdom.verification.api;

import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;

import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.application.LocationCheckInService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 principal의 사용자 ID와 검증한 관측 입력을 근접 체크인 서비스에 전달한다.
 * 생성 응답은 201이며 본인 목록은 1부터 시작하는 페이지와 최대 100건 크기를 받는다.
 */
@RestController
@RequestMapping("/location-check-ins")
@RequiredArgsConstructor
@AuthenticatedOnly
@SecurityRequirement(name = "bearerAuth")
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.VISIT)
@org.springframework.validation.annotation.Validated
public class LocationCheckInController {
    private final LocationCheckInService service;

    @PostMapping
    @Operation(summary = "클라이언트 위치 기반 방문 체크인",
            description = "클라이언트가 제출한 위치와 장소 좌표의 근접성을 확인하며 실제 GPS를 증명하지 않습니다.")
    @ApiResponse(responseCode = "201", description = "위치 근접성 확인 기록 생성")
    @ApiResponse(responseCode = "400", description = "관측 시각 또는 정확도 검증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "활성 관광객 계정이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "체크인 가능한 장소를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "일일 중복 체크인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "장소 체크인 허용 반경 밖",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<LocationCheckInResponse> checkIn(@Valid @RequestBody LocationCheckInRequest request,
            @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkIn(user.userId(), request));
    }

    @GetMapping
    @Operation(summary = "내 위치 체크인 목록 조회")
    public LocationCheckInPageResponse listMine(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @CurrentUser JwtAuthenticatedUser user) {
        return service.listMine(user.userId(), page, limit);
    }
}
