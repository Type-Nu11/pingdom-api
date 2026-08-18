package com.typenull.pingdom.place.domain.place.category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class PlaceCategoryPolicy {

    public static final String MUSIC = "MUSIC";
    public static final String RESTAURANT = "RESTAURANT";
    public static final String POP_UP = "POP_UP";
    public static final String FASHION = "FASHION";
    public static final String BEAUTY = "BEAUTY";
    public static final String EXHIBITION = "EXHIBITION";
    public static final String CAFE = "CAFE";
    public static final String OTHER = "OTHER";
    @Deprecated public static final String TOURISM = OTHER;
    @Deprecated public static final String SCENERY = OTHER;
    @Deprecated public static final String CULTURE = EXHIBITION;
    @Deprecated public static final String SHOPPING = FASHION;
    @Deprecated public static final String ACCOMMODATION = OTHER;
    @Deprecated public static final String EXPERIENCE = OTHER;

    private static final Map<String, String> STANDARD_CATEGORY_MAP = createStandardCategoryMap();

    private PlaceCategoryPolicy() {
    }

    public static String normalize(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }

        String trimmed = category.trim();
        return STANDARD_CATEGORY_MAP.getOrDefault(trimmed.toLowerCase(Locale.ROOT), OTHER);
    }

    private static Map<String, String> createStandardCategoryMap() {
        Map<String, String> categories = new LinkedHashMap<>();
        register(categories, CAFE, "카페", "커피", "coffee", "cafe", "디저트", "베이커리");
        register(categories, RESTAURANT, "식당", "맛집", "음식점", "레스토랑", "restaurant", "food");
        register(categories, EXHIBITION, "전시", "박물관", "미술관", "문화", "culture");
        register(categories, MUSIC, "공연", "음악", "music");
        register(categories, FASHION, "쇼핑", "패션", "마트", "편집샵", "shopping", "fashion");
        register(categories, BEAUTY, "뷰티", "미용", "beauty");
        register(categories, POP_UP, "팝업", "pop-up", "popup");
        register(categories, OTHER, "관광", "관광지", "명소", "여행", "자연", "공원", "산책", "야경", "숙박", "체험", "tour", "travel", "scenery", "hotel", "stay", "activity", "sports", "스포츠");
        return Collections.unmodifiableMap(categories);
    }

    private static void register(Map<String, String> categories, String standardCategory, String... aliases) {
        categories.put(standardCategory.toLowerCase(Locale.ROOT), standardCategory);
        for (String alias : aliases) {
            categories.put(alias.toLowerCase(Locale.ROOT), standardCategory);
        }
    }
}
