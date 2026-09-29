package com.typenull.pingdom.place.api.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 조회 시점의 자동차 추천 경로 요청. 동일 출발·도착 좌표는 허용하지 않습니다.")
public record RouteRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RouteCoordinate origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RouteCoordinate destination,
        @Schema(description = "car만 지원. walk/transit/bike는 422, 그 밖의 값은 400", example = "car", requiredMode = Schema.RequiredMode.REQUIRED)
        String mode
) { }
