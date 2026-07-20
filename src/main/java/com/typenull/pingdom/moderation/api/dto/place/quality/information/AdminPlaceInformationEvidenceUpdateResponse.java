package com.typenull.pingdom.moderation.api.dto.place.quality.information;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 정보 증빙 변경 응답")
public record AdminPlaceInformationEvidenceUpdateResponse(
        AdminPlaceInformationEvidenceItem evidence,
        String message
) {
}
