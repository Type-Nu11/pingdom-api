package com.typenull.pingdom.community.api.dto;

import com.typenull.pingdom.community.domain.CommunityReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "커뮤니티 신고 접수 결과")
public record CommunityReportCreateResponse(
        @Schema(description = "신고 ID", example = "1") Long reportId,
        @Schema(description = "신고 처리 상태", example = "PENDING") CommunityReportStatus status
) {
}
