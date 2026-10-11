package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "커뮤니티 게시글 댓글 목록 조회 응답")
public record CommunityPostCommentListResponse(
        @Schema(description = "댓글 목록") List<Item> comments,
        @Schema(description = "현재 페이지", example = "1") int page,
        @Schema(description = "페이지 크기", example = "20") int limit,
        @Schema(description = "전체 댓글 수", example = "42") long totalCount,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext
) {

    @Schema(name = "CommunityCommentSummary", description = "댓글 목록 항목")
    public record Item(
            @Schema(description = "댓글 ID", example = "1") Long commentId,
            @Schema(description = "댓글 내용", example = "저도 가보고 싶네요!") String content,
            @Schema(description = "작성자 ID", example = "7") Long authorId,
            @Schema(description = "작성자 표시 이름", example = "pingdom") String authorName,
            @Schema(description = "댓글 작성 시각") LocalDateTime createdAt,
            @Schema(description = "최상위 댓글이면 null, 대댓글이면 부모 ID") Long parentCommentId,
            long likeCount,
            @Schema(description = "현재 인증 사용자 좋아요 여부") boolean liked
    ) {
        public Item(Long id, String content, Long authorId, String authorName, LocalDateTime createdAt) {
            this(id, content, authorId, authorName, createdAt, null, 0, false);
        }
    }
}
