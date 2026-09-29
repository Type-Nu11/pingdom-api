package com.typenull.pingdom.place.api.dto.route;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;

/** WGS84 좌표. JSON 문자열을 숫자로 자동 변환하지 않아 앱 계약 오류를 조기에 차단합니다. */
public record RouteCoordinate(
        @Schema(description = "WGS84 위도, JSON 숫자", minimum = "-90", maximum = "90", example = "37.5665", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = StrictNumberDeserializer.class) Double latitude,
        @Schema(description = "WGS84 경도, JSON 숫자", minimum = "-180", maximum = "180", example = "126.9780", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonDeserialize(using = StrictNumberDeserializer.class) Double longitude
) {
    @JsonIgnore
    public boolean isValid() {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static class StrictNumberDeserializer extends JsonDeserializer<Double> {
        @Override
        public Double deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.currentToken().isNumeric()) {
                return (Double) context.handleUnexpectedToken(Double.class, parser);
            }
            return parser.getDoubleValue();
        }
    }
}
