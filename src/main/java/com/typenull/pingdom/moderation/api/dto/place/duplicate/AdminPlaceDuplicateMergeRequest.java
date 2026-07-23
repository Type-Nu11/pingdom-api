package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import jakarta.validation.constraints.NotNull;

public record AdminPlaceDuplicateMergeRequest(
        @NotNull(message = "병합 대상 장소를 선택해주세요.")
        Long targetPlaceId
) {
}
