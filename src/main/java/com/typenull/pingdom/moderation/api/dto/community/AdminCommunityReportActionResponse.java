package com.typenull.pingdom.moderation.api.dto.community;

import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.domain.CommunityReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 커뮤니티 신고 처리 결과")
public record AdminCommunityReportActionResponse(
        Long reportId,
        CommunityReportStatus status,
        CommunityReportTargetType targetType,
        Long targetId,
        boolean targetHidden,
        LocalDateTime processedAt
) {
}
