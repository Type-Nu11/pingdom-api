package com.typenull.pingdom.post.api.dto.post;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "게시글 상세 조회 응답")
public record PostDetailResponse(
        @Schema(description = "게시글 ID", example = "10")
        Long id,
        @Schema(description = "게시글 제목", example = "남강 야경")
        String title,
        @Schema(description = "게시글 이미지 URL", example = "https://example.com/images/post-10.jpg")
        String imageUrl,
        @Schema(description = "게시글 설명", example = "남강 산책 중 찍은 사진입니다.")
        String description,
        @Schema(description = "작성자 사용자 ID", example = "3")
        Long userId,
        @Schema(description = "작성자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "작성 시각", example = "2026-06-04T16:20:00")
        LocalDateTime createdAt,
        @Schema(description = "좋아요 수", example = "12")
        long likeCount,
        @Schema(description = "좋아요 여부")
        boolean likedByMe,
        @Schema(description = "연결된 장소 ID", example = "5")
        Long placeId,
        @Schema(description = "연결된 장소명", example = "진주성")
        String placeName,
        @Schema(description = "연결된 장소 주소", example = "경상남도 진주시 남강로 626")
        String placeAddress,
        @Schema(description = "장소 위도", example = "35.1894")
        Double latitude,
        @Schema(description = "장소 경도", example = "128.0789")
        Double longitude,
        @Schema(description = "연결된 장소 성장 상태")
        PlaceGrowthSnapshot placeGrowth
) {
}
