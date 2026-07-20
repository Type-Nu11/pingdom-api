package com.typenull.pingdom.moderation.api.dto.place.quality.information;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidenceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 정보 증빙 등록 요청")
public record AdminPlaceInformationEvidenceCreateRequest(
        @NotNull
        PlaceInformationSourceType sourceType,
        @NotNull
        PlaceInformationEvidenceType evidenceType,
        @Schema(nullable = true, description = "외부 장소 ID, 사업자 등록 번호 등 외부 참조값")
        @Size(max = 100)
        String externalReference,
        @Schema(nullable = true, description = "증빙 URL")
        @Size(max = 500)
        String referenceUrl,
        @Schema(nullable = true, description = "관리자 메모 또는 증빙 설명")
        @Size(max = 1000)
        String description,
        @Schema(nullable = true, description = "증빙 제출 사용자 ID. 미입력 시 관리자 ID를 사용")
        Long submittedByUserId
) {
}
