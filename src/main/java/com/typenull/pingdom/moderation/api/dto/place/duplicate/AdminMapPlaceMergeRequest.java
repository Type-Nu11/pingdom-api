package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import jakarta.validation.constraints.Positive;

public record AdminMapPlaceMergeRequest(
        @Positive(message = "원본 장소 ID는 양수여야 합니다.")
        Long sourcePlaceId,

        @Positive(message = "대상 장소 ID는 양수여야 합니다.")
        Long targetPlaceId,

        @Positive(message = "중복 장소 후보 ID는 양수여야 합니다.")
        Long candidateId
) {

    public AdminMapPlaceMergeRequest(Long sourcePlaceId, Long targetPlaceId) {
        this(sourcePlaceId, targetPlaceId, null);
    }
}
