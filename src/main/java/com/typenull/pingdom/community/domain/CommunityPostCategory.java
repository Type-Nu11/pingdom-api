package com.typenull.pingdom.community.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 커뮤니티 게시글 작성과 조회에서 공통으로 사용하는 카테고리 목록이다.
 *
 * <p>카테고리 식별자는 게시글 저장 시 사용할 안정적인 값이며, 화면에는 displayName만 노출한다.</p>
 */
public enum CommunityPostCategory {
    PLACE("PLACE", "장소", 1, true),
    TRAVEL("TRAVEL", "여행", 2, true),
    MONEY("MONEY", "돈", 3, true);

    private final String id;
    private final String displayName;
    private final int displayOrder;
    private final boolean enabled;

    CommunityPostCategory(String id, String displayName, int displayOrder, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static List<CommunityPostCategory> enabledCategories() {
        return Arrays.stream(values())
                .filter(CommunityPostCategory::isEnabled)
                .sorted(Comparator.comparingInt(CommunityPostCategory::getDisplayOrder))
                .toList();
    }

    public static Optional<CommunityPostCategory> findEnabledById(String categoryId) {
        return enabledCategories().stream()
                .filter(category -> category.id.equals(categoryId))
                .findFirst();
    }
}
