package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "커뮤니티 게시글 카테고리 목록 응답")
public record CommunityPostCategoryListResponse(
        @Schema(description = "작성 및 조회에 사용할 활성 카테고리 목록")
        List<Item> categories
) {
    @Schema(name = "CommunityCategoryItem", description = "커뮤니티 카테고리 목록 항목")
    public record Item(
            @Schema(description = "저장과 요청에 사용할 카테고리 식별자", example = "PLACE")
            String categoryId,
            @Schema(description = "화면 표시용 카테고리 이름", example = "장소")
            String categoryName
    ) {
    }
}
