package com.typenull.pingdom.place.api.dto.route;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "TMAP 보행자 추천 경로")
public record WalkingRouteResponse(
        @Schema(example = "walk", allowableValues = "walk", requiredMode = Schema.RequiredMode.REQUIRED) String mode,
        @Schema(example = "tmap", allowableValues = "tmap", requiredMode = Schema.RequiredMode.REQUIRED) String provider,
        @Schema(description = "공급자가 계산한 총거리(m)", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long distanceMeters,
        @Schema(description = "예상 소요시간(초)", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED) long durationSeconds,
        @ArraySchema(minItems = 2, arraySchema = @Schema(description = "출발→도착 순서의 전체 보행 경로", requiredMode = Schema.RequiredMode.REQUIRED))
        List<RouteCoordinate> path,
        @Schema(description = "공급자가 제공한 순서의 보행 구간 안내") List<Step> steps
) implements RouteResult {
    public record Step(
            @Schema(description = "보행 안내 문구") String instruction,
            @Schema(description = "구간 거리(m)", minimum = "0") long distanceMeters,
            @Schema(description = "구간 예상 소요시간(초)", minimum = "0") long durationSeconds,
            List<RouteCoordinate> path
    ) { }
}
