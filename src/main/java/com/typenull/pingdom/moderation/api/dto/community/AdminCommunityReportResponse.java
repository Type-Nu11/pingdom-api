package com.typenull.pingdom.moderation.api.dto.community;

import com.typenull.pingdom.community.domain.CommunityReportReason;
import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.domain.CommunityReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 커뮤니티 신고 상세 응답")
public record AdminCommunityReportResponse(
        Long reportId,
        CommunityReportTargetType targetType,
        Long targetId,
        boolean targetHidden,
        Long reporterUserId,
        CommunityReportReason reason,
        String description,
        CommunityReportStatus status,
        LocalDateTime createdAt,
        Long processedByAdminUserId,
        LocalDateTime processedAt
) {
}
