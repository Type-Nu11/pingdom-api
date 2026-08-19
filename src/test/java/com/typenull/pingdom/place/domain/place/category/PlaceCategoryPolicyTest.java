package com.typenull.pingdom.place.domain.place.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceCategoryPolicyTest {

    @Test
    void canonicalCategoriesExposeExpectedKoreanDisplayNames() {
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

    @Test
    void culturalHeritageAliasesNormalizeToCanonicalCategory() {
        assertThat(PlaceCategoryPolicy.canonicalOrNull("문화재"))
                .isEqualTo(PlaceCategoryPolicy.CULTURAL_HERITAGE);
        assertThat(PlaceCategoryPolicy.normalize(" heritage "))
                .isEqualTo(PlaceCategoryPolicy.CULTURAL_HERITAGE);
        assertThat(PlaceCategoryPolicy.normalizedAliases(PlaceCategoryPolicy.CULTURAL_HERITAGE))
                .contains("cultural_heritage", "문화재", "유적", "heritage", "cultural heritage");
    }

    @Test
    void unsupportedLegacyCategoryIsUncategorizedOnlyForResponses() {
        assertThat(PlaceCategoryPolicy.canonicalOrNull("legacy-free-text")).isNull();
        assertThat(PlaceCategoryPolicy.displayName("legacy-free-text")).isEqualTo("미분류");
        assertThat(PlaceCategoryPolicy.normalize("legacy-free-text"))
                .isEqualTo(PlaceCategoryPolicy.OTHER);
    }
}
