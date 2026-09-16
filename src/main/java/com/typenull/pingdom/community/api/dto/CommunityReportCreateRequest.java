package com.typenull.pingdom.community.api.dto;

import com.typenull.pingdom.community.domain.CommunityReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "커뮤니티 신고 요청")
public record CommunityReportCreateRequest(
        @Schema(description = "신고 사유", example = "SPAM")
        @NotNull CommunityReportReason reason,
        @Schema(description = "상세 설명", example = "같은 광고를 반복 게시합니다.")
        @NotBlank @Size(max = 500) String description
) {
}
