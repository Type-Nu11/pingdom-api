package com.typenull.pingdom.place.api.dto.place.information.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 정보 신고 반박 생성 요청")
public record PlaceInformationDisputeCreateRequest(
        @Size(min = 1, max = 1000)
        String description,
        @Schema(nullable = true)
        @Size(max = 500)
        String evidenceUrl
) {
}
