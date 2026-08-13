package com.typenull.pingdom.place.domain.place.category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class PlaceCategoryPolicy {

    public static final String CAFE = "카페";
    public static final String RESTAURANT = "식당";
    public static final String TOURISM = "관광";
    public static final String SCENERY = "풍경";
    public static final String CULTURE = "문화";
    public static final String SHOPPING = "쇼핑";
    public static final String ACCOMMODATION = "숙박";
    public static final String EXPERIENCE = "체험";

    private static final Map<String, String> STANDARD_CATEGORY_MAP = createStandardCategoryMap();

    private PlaceCategoryPolicy() {
    }

    public static String normalize(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }

        String trimmed = category.trim();
        return STANDARD_CATEGORY_MAP.getOrDefault(trimmed.toLowerCase(Locale.ROOT), trimmed.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> createStandardCategoryMap() {
        Map<String, String> categories = new LinkedHashMap<>();
        register(categories, CAFE, "커피", "coffee", "cafe", "디저트", "베이커리");
        register(categories, RESTAURANT, "맛집", "음식점", "레스토랑", "restaurant", "food");
        register(categories, TOURISM, "관광지", "명소", "여행", "tour", "travel");
        register(categories, SCENERY, "자연", "공원", "산책", "야경", "view", "scenery");
        register(categories, CULTURE, "전시", "박물관", "미술관", "공연", "culture");
        register(categories, SHOPPING, "마트", "편집샵", "shopping");
        register(categories, ACCOMMODATION, "호텔", "모텔", "펜션", "숙소", "hotel", "stay");
        register(categories, EXPERIENCE, "액티비티", "activity", "sports", "스포츠");
        return Collections.unmodifiableMap(categories);
    }

    private static void register(Map<String, String> categories, String standardCategory, String... aliases) {
        categories.put(standardCategory.toLowerCase(Locale.ROOT), standardCategory);
        for (String alias : aliases) {
            categories.put(alias.toLowerCase(Locale.ROOT), standardCategory);
        }
    }
}
