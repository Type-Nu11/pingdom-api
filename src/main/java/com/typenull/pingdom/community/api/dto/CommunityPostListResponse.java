package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "카테고리별 커뮤니티 게시글 목록 조회 응답")
public record CommunityPostListResponse(
        @Schema(description = "게시글 목록") List<Item> posts,
        @Schema(description = "현재 페이지", example = "1") int page,
        @Schema(description = "페이지 크기", example = "20") int limit,
        @Schema(description = "전체 게시글 수", example = "42") long totalCount,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext
) {

    @Schema(name = "CommunityPostSummary", description = "게시글 목록 항목")
    public record Item(
            @Schema(description = "게시글 ID", example = "1") Long postId,
            @Schema(description = "게시글 제목", example = "대소고 다녀왔어요") String title,
            @Schema(description = "본문의 공백을 정리한 최대 120자 미리보기") String preview,
            @Schema(nullable = true, description = "첫 사진 URL. 사진이 없으면 null") String thumbnailUrl,
            @Schema(description = "전체 게시글 사진 수") long imageCount,
            @Schema(nullable = true, description = "첫 연결 장소 지역. 연결 장소가 없거나 삭제되면 null") String region,
            LocalDateTime createdAt,
            long viewCount,
            long likeCount,
            @Schema(description = "공개 댓글·대댓글 수. 숨김 부모의 대댓글 제외") long commentCount,
            CommunityPostDetailResponse.Category category,
            @Schema(nullable = true, description = "작성자 국가 코드. 미지원·미상·탈퇴는 null") String country
    ) {
    }
}
