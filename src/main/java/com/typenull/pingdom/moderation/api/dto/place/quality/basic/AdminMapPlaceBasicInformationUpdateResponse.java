package com.typenull.pingdom.moderation.api.dto.place.quality.basic;

import com.typenull.pingdom.place.domain.place.category.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 장소 기본 정보 수정 응답")
public record AdminMapPlaceBasicInformationUpdateResponse(
        Long placeId,
        String name,
        PlaceCategory category,
        @Schema(description = "감사 로그에 기록된 기본 정보 수정 시각")
        LocalDateTime modifiedAt,
        String message
) {
}
