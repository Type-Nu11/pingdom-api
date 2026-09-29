package com.typenull.pingdom.place.api.dto.route;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "NAVER traoptimal 첫 번째 실제 자동차 경로. 입력 좌표와 도로 위 시작·끝 좌표는 다를 수 있습니다.")
public record RouteResponse(
        @Schema(example = "car", allowableValues = "car", requiredMode = Schema.RequiredMode.REQUIRED) String mode,
        @Schema(example = "naver", allowableValues = "naver", requiredMode = Schema.RequiredMode.REQUIRED) String provider,
        @Schema(description = "공급자가 계산한 총거리(m)", minimum = "0", example = "12500", requiredMode = Schema.RequiredMode.REQUIRED) long distanceMeters,
        @Schema(description = "예상 소요시간(초), 공급자 밀리초를 올림하여 변환", minimum = "0", example = "1800", requiredMode = Schema.RequiredMode.REQUIRED) long durationSeconds,
        @ArraySchema(minItems = 2, arraySchema = @Schema(description = "출발→도착 순서의 도로 형상 전체. 직선 대체·끝점 덮어쓰기를 하지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED))
        List<RouteCoordinate> path
) { }
