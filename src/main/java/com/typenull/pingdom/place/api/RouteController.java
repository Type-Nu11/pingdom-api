package com.typenull.pingdom.place.api;

import com.typenull.pingdom.place.api.dto.route.RouteRequest;
import com.typenull.pingdom.place.api.dto.route.RouteResponse;
import com.typenull.pingdom.place.application.service.route.RouteQueryService;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.ratelimit.annotation.RateLimited;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routes")
@ApiAudience(ApiAudience.Group.APP)
@Tag(name = SwaggerTagCatalog.PLACE_DISCOVERY)
public class RouteController {
    private final RouteQueryService service;

    public RouteController(RouteQueryService service) {
        this.service = service;
    }

    @PostMapping
    @RateLimited(RateLimitAction.ROUTE_QUERY)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "자동차 추천 경로 조회", description = "WGS84 출발·도착 좌표로 NAVER Directions 5 traoptimal 경로 1개를 조회합니다. "
            + "JWT 인증 필수이며 공급자 키는 서버에서 관리합니다. 경유지·미래 출발 시각·복수 경로·도보·대중교통·자전거는 지원하지 않습니다. "
            + "거리 m, 소요시간 초(밀리초 올림), 전체 경로 좌표를 반환하며 저장·캐시·자동 재시도를 하지 않습니다. "
            + "기본 사용자당 10회/분, IP당 100회/분이며 설정에 따라 달라질 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실제 도로 형상 경로", content = @Content(schema = @Schema(implementation = RouteResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_ROUTE_REQUEST: 누락·문자열·비유한 좌표, 범위 오류, 동일 좌표, 알 수 없는 mode", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "기존 JWT 인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "기존 접근 권한 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "UNSUPPORTED_ROUTE_MODE 또는 ROUTE_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "unsupported", value = "{\"message\":\"지원하지 않는 이동수단입니다.\",\"code\":\"UNSUPPORTED_ROUTE_MODE\"}"),
                    @ExampleObject(name = "notFound", value = "{\"message\":\"자동차 경로를 찾을 수 없습니다.\",\"code\":\"ROUTE_NOT_FOUND\"}") })),
            @ApiResponse(responseCode = "429", description = "ROUTE_RATE_LIMITED: 사용자/IP 제한 초과", headers = @Header(name = "Retry-After", description = "두 제한 구간 중 긴 시간(초), 안전한 재시도 대기 상한", schema = @Schema(type = "integer")), content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "ROUTE_PROVIDER_UNAVAILABLE: 비활성·미설정·공급자 인증/쿼터/장애/비정상 응답. 호출 제한 저장소 장애는 기존 RATE_LIMIT_UNAVAILABLE", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "ROUTE_PROVIDER_TIMEOUT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RouteResponse> findRoute(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples = @ExampleObject(value = "{\"origin\":{\"latitude\":37.5665,\"longitude\":126.9780},\"destination\":{\"latitude\":37.4979,\"longitude\":127.0276},\"mode\":\"car\"}")))
            @Valid @RequestBody RouteRequest request,
            @Parameter(hidden = true) @CurrentUser JwtAuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findRoute(request));
    }
}
