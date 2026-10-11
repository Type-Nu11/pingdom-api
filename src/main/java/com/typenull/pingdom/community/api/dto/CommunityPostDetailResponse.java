package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "커뮤니티 게시글 상세 조회 응답")
public record CommunityPostDetailResponse(
        @Schema(description = "게시글 ID", example = "1") Long postId,
        @Schema(description = "게시글 제목", example = "대소고 다녀왔어요") String title,
        @Schema(description = "게시글 본문", example = "즐거운 경험이었습니다.") String content,
        @Schema(description = "연결 장소 목록") List<Place> places,
        @Schema(description = "작성자 정보") Author author,
        @Schema(description = "게시글 카테고리") Category category,
        @Schema(description = "게시글 작성 시각") LocalDateTime createdAt,
        @Schema(description = "요청 순서의 게시글 사진. 사진이 없으면 빈 배열") List<Image> images,
        @Schema(nullable = true, description = "첫 연결 장소의 지역. 연결 장소가 없거나 삭제되면 null") String region,
        @Schema(description = "사용자·게시글·KST 날짜별 조회수. POST /community/posts/{postId}/view로 기록") long viewCount
) {

    @Schema(name = "CommunityPostImage", description = "순서가 보존된 게시글 사진")
    public record Image(Long imageId, String imageUrl, int displayOrder) { }

    @Schema(description = "게시글 작성자 표시 정보")
    public record Author(
            @Schema(description = "게시글 작성자 ID") Long authorId,
            @Schema(description = "작성자 표시 이름. 탈퇴 또는 조회 불가 시 대체 이름") String authorName,
            @Schema(nullable = true, description = "프로필 이미지 URL. 없거나 탈퇴한 경우 null") String profileImageUrl
    ) {
    }

    @Schema(name = "CommunityPostCategorySummary", description = "게시글 카테고리 표시 정보")
    public record Category(
            @Schema(description = "카테고리 식별자", example = "TRAVEL") String categoryId,
            @Schema(description = "카테고리 표시 이름", example = "여행") String categoryName
    ) {
    }

    @Schema(description = "게시글에 연결된 장소")
    public record Place(
            @Schema(description = "장소 ID", example = "1") Long placeId,
            @Schema(description = "현재 장소 이름. 삭제된 장소는 삭제 안내 문구", example = "대소고") String placeName,
            @Schema(description = "삭제된 장소 여부", example = "false") boolean deleted,
            @Schema(nullable = true, description = "장소 대표 이미지. 없거나 삭제된 장소는 null") String imageUrl,
            @Schema(nullable = true, description = "장소 분류. 없거나 삭제된 장소는 null") String category,
            @Schema(nullable = true, description = "행정구역명. 미등록 지역은 장소 주소, 삭제된 장소는 null") String region
    ) {
    }
}
