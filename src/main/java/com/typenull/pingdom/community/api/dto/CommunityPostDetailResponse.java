package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "커뮤니티 게시글 상세 조회 응답")
public record CommunityPostDetailResponse(
        @Schema(description = "게시글 ID", example = "1") Long postId,
        @Schema(description = "게시글 제목", example = "대소고 다녀왔어요") String title,
        @Schema(description = "게시글 본문", example = "즐거운 경험이었습니다.") String content,
        @Schema(description = "연결 장소 목록") List<Place> places
) {

    @Schema(description = "게시글에 연결된 장소")
    public record Place(
            @Schema(description = "장소 ID", example = "1") Long placeId,
            @Schema(description = "현재 장소 이름. 삭제된 장소는 삭제 안내 문구", example = "대소고") String placeName,
            @Schema(description = "삭제된 장소 여부", example = "false") boolean deleted
    ) {
    }
}
