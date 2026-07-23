package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPlaceDuplicateDecisionRequest(
        @NotBlank(message = "판정 사유를 입력해주세요.")
        @Size(max = 500, message = "판정 사유는 500자 이하여야 합니다.")
        String reviewNote
) {
}
