package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내 게시글 목록. 숨김 글 포함, 삭제 글 제외. createdAt DESC, postId DESC 정렬")
public record MyCommunityPostListResponse(
        List<Item> posts, int page, int limit, long totalElements, int totalPages, boolean hasNext
) {
    @Schema(name = "MyCommunityPostSummary", description = "본인 게시글 카드. hidden=true인 글은 공개 상세 조회와 수정 불가, 삭제 가능")
    public record Item(
            Long postId, String title, String contentPreview, String imageUrl, long imageCount,
            String countryCode, String region, LocalDateTime createdAt, LocalDateTime updatedAt,
            long viewCount, long likeCount, long commentCount, CommunityPostDetailResponse.Category category,
            boolean hidden
    ) {}
}
