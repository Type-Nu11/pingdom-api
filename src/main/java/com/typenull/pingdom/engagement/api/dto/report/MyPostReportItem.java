package com.typenull.pingdom.engagement.api.dto.report;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 신고 내역 항목")
public record MyPostReportItem(
        @Schema(description = "신고 ID", example = "10")
        Long reportId,
        @Schema(description = "신고한 게시글 ID", example = "101")
        Long postId,
        @Schema(description = "게시글 제목", example = "남강 야경", nullable = true)
        String title,
        @Schema(description = "게시글 이미지 URL", example = "https://example.com/images/post-101.jpg")
        String imageUrl,
        @Schema(description = "게시글 썸네일 이미지 URL", example = "https://example.com/images/post-101-thumbnail.jpg", nullable = true)
        String thumbnailUrl,
        @Schema(description = "게시글 설명", example = "남강 산책 중 찍은 사진입니다.", nullable = true)
        String description,
        @Schema(description = "게시글 작성자 사용자 ID", example = "3", nullable = true)
        Long postUserId,
        @Schema(description = "게시글 작성자 아이디", example = "pingdom_user", nullable = true)
        String postUsername,
        @Schema(description = "게시글 작성 시각", example = "2026-06-04T16:20:00", nullable = true)
        LocalDateTime postCreatedAt,
        @Schema(description = "연결된 장소 ID", example = "5", nullable = true)
        Long placeId,
        @Schema(description = "연결된 장소명", example = "진주성", nullable = true)
        String placeName,
        @Schema(description = "신고 사유", example = "부적절한 사진입니다.")
        String reason,
        @Schema(description = "신고 처리 상태", example = "PENDING")
        PostReportStatus status
) {
}
