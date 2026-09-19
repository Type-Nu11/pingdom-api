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
        @Schema(description = "원문 글 ID. POST는 targetId와 같고 COMMENT는 대상 댓글이 속한 글 ID입니다. "
                + "신고 대상과 댓글의 원문 관계는 DB 제약으로 보장되며, 숨김 여부와 관계없이 반환합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED, nullable = false, example = "101")
        Long postId,
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
