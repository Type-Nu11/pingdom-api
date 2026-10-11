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
        @Schema(description = "표시 순서대로 정렬된 게시글 사진. position은 0부터 시작") List<Image> images,
        @Schema(description = "ISO alpha-2 국가 코드. 기존 미설정 글은 null") String countryCode,
        @Schema(description = "작성자가 지정한 표시 지역. 미설정은 null") String region,
        @Schema(description = "정상 상세 요청 횟수. 사용자별 고유 조회가 아님") long viewCount
) {

    public CommunityPostDetailResponse(Long id, String title, String content, List<Place> places,
            Author author, Category category, LocalDateTime createdAt) {
        this(id, title, content, places, author, category, createdAt, List.of(), null, null, 0);
    }
    @Schema(name = "CommunityPostImageItem")
    public record Image(Long imageId, String imageUrl, int position) {}

    @Schema(description = "게시글 작성자 표시 정보")
    public record Author(
            @Schema(description = "게시글 작성자 ID") Long authorId,
            @Schema(description = "작성자 표시 이름. 탈퇴 또는 조회 불가 시 대체 이름") String authorName,
            @Schema(description = "프로필 이미지 URL. 없거나 탈퇴한 경우 null") String profileImageUrl
    ) {
    }

    @Schema(description = "게시글 카테고리 표시 정보")
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
            @Schema(description = "장소 대표 이미지. 미설정 또는 삭제 시 null") String imageUrl,
            @Schema(description = "기존 장소 분류. 미설정 또는 삭제 시 null") String category,
            @Schema(description = "기존 장소 지역 코드") String regionCode,
            @Schema(description = "장소 표시 주소") String address,
            @Schema(description = "기존 지역 사전의 표시 이름. 미해석 또는 삭제 시 null") String region
    ) {
        public Place(Long placeId, String placeName, boolean deleted) {
            this(placeId, placeName, deleted, null, null, null, null, null);
        }
    }
}
