package com.typenull.pingdom.moderation.api.dto.place.quality.information;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 장소 정보 증빙 목록 응답")
public record AdminPlaceInformationEvidenceResponse(
        Long placeId,
        List<AdminPlaceInformationEvidenceItem> evidences
) {
}
