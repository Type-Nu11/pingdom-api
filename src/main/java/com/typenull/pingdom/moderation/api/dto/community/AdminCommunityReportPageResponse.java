package com.typenull.pingdom.moderation.api.dto.community;

import com.typenull.pingdom.community.domain.CommunityReportReason;
import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.domain.CommunityReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "관리자 커뮤니티 신고 목록 응답")
public record AdminCommunityReportPageResponse(
        List<Item> reports,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {

    public record Item(
            Long reportId,
            CommunityReportTargetType targetType,
            Long targetId,
            boolean targetHidden,
            Long reporterUserId,
            CommunityReportReason reason,
            CommunityReportStatus status,
            LocalDateTime createdAt,
            LocalDateTime processedAt
    ) {
    }
}
