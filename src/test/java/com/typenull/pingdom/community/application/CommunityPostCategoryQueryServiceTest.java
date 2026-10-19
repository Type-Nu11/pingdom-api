package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.community.api.dto.CommunityPostCategoryListResponse;
import org.junit.jupiter.api.Test;

class CommunityPostCategoryQueryServiceTest {

    private final CommunityPostCategoryQueryService service = new CommunityPostCategoryQueryService();

    /**
     * 카테고리 조회가 스팟·다이어리·가계부의 식별자와 한국어 이름을 정해진 표시 순서대로 반환하는지 검증.
     */
    @Test
    void returnsOrderedActiveCategories() {
        CommunityPostCategoryListResponse response = service.findCategories();

        assertThat(response.categories()).extracting(
                        CommunityPostCategoryListResponse.Item::categoryId,
                        CommunityPostCategoryListResponse.Item::categoryName
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PLACE", "스팟"),
                        org.assertj.core.groups.Tuple.tuple("TRAVEL", "다이어리"),
                        org.assertj.core.groups.Tuple.tuple("MONEY", "가계부")
                );
    }
}
