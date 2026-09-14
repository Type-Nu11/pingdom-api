package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.community.api.dto.CommunityPostCategoryListResponse;
import org.junit.jupiter.api.Test;

class CommunityPostCategoryQueryServiceTest {

    private final CommunityPostCategoryQueryService service = new CommunityPostCategoryQueryService();

    @Test
    void 활성_카테고리를_표시_순서대로_식별자와_이름으로_반환한다() {
        CommunityPostCategoryListResponse response = service.findCategories();

        assertThat(response.categories()).extracting(
                        CommunityPostCategoryListResponse.Item::categoryId,
                        CommunityPostCategoryListResponse.Item::categoryName
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PLACE", "장소"),
                        org.assertj.core.groups.Tuple.tuple("TRAVEL", "여행"),
                        org.assertj.core.groups.Tuple.tuple("MONEY", "돈")
                );
    }
}
