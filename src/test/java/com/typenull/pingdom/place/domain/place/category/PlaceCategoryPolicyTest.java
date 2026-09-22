package com.typenull.pingdom.place.domain.place.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceCategoryPolicyTest {

    /** 정규 카테고리 코드·한국어 표시명과 enum 집합이 일치해 응답 분류 계약을 유지하는지 확인한다. */
    @Test
    void mapsCanonicalCategoryLabels() {
        Map<String, String> categoryNames = Map.of(
                PlaceCategoryPolicy.RESTAURANT, "음식점",
                PlaceCategoryPolicy.MUSIC, "음악",
                PlaceCategoryPolicy.POP_UP, "팝업",
                PlaceCategoryPolicy.FASHION, "패션",
                PlaceCategoryPolicy.BEAUTY, "뷰티",
                PlaceCategoryPolicy.EXHIBITION, "전시",
                PlaceCategoryPolicy.CAFE, "카페",
                PlaceCategoryPolicy.CULTURAL_HERITAGE, "문화재",
                PlaceCategoryPolicy.OTHER, "기타"
        );

        assertThat(categoryNames)
                .allSatisfy((category, displayName) -> {
                    assertThat(PlaceCategoryPolicy.canonicalOrNull(category)).isEqualTo(category);
                    assertThat(PlaceCategoryPolicy.displayName(category)).isEqualTo(displayName);
                });
        assertThat(PlaceCategory.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrderElementsOf(categoryNames.keySet());
    }

    /** 문화재의 한국어·영어·공백 포함 별칭을 정규 코드로 변환하고 알려진 별칭 집합을 제공하는지 확인한다. */
    @Test
    void normalizesCulturalHeritageAliases() {
        assertThat(PlaceCategoryPolicy.canonicalOrNull("문화재"))
                .isEqualTo(PlaceCategoryPolicy.CULTURAL_HERITAGE);
        assertThat(PlaceCategoryPolicy.normalize(" heritage "))
                .isEqualTo(PlaceCategoryPolicy.CULTURAL_HERITAGE);
        assertThat(PlaceCategoryPolicy.normalizedAliases(PlaceCategoryPolicy.CULTURAL_HERITAGE))
                .contains("cultural_heritage", "문화재", "유적", "heritage", "cultural heritage");
    }

    /** 지원하지 않는 기존 문자열은 정규 코드 null·응답 미분류로 표시하되 저장 정규화에서는 OTHER로 처리하는지 확인한다. */
    @Test
    void handlesUnknownLegacyCategory() {
        assertThat(PlaceCategoryPolicy.canonicalOrNull("legacy-free-text")).isNull();
        assertThat(PlaceCategoryPolicy.displayName("legacy-free-text")).isEqualTo("미분류");
        assertThat(PlaceCategoryPolicy.normalize("legacy-free-text"))
                .isEqualTo(PlaceCategoryPolicy.OTHER);
    }
}
