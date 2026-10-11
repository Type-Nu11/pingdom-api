package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "커뮤니티 게시글 등록 요청")
public record CommunityPostCreateRequest(
        @Schema(description = "게시글 카테고리 식별자", example = "PLACE")
        @NotBlank @Size(max = 30) String categoryId,
        @Schema(description = "게시글 제목", example = "대소고 다녀왔어요")
        @NotBlank @Size(max = 50) String title,
        @Schema(description = "게시글 본문", example = "즐거운 경험이었습니다.")
        @NotBlank @Size(max = 5000) String content,
        @Schema(description = "연결할 장소 ID 목록. 장소 카테고리에서는 1개 이상 필수입니다.", example = "[1, 2]")
        List<@NotNull @Positive Long> placeIds,
        @Schema(description = "업로드한 본인 이미지 ID. 입력 순서대로 표시하며 최대 10장")
        @Size(max = 10) List<@NotNull @Positive Long> imageIds,
        @Schema(description = "ISO 3166-1 alpha-2 국가 코드. 미설정은 null", example = "KR")
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @Schema(description = "작성자가 지정한 표시 지역. 미설정은 null", example = "서울")
        @Size(max = 100) String region
) {
    public CommunityPostCreateRequest(String categoryId, String title, String content, List<Long> placeIds) {
        this(categoryId, title, content, placeIds, null, null, null);
    }
}
