package com.typenull.pingdom.place.domain.place.core;

/**
 * 추세 재구성을 위한 초기 활성·추가·제거 이벤트입니다.
 * BASELINE_ACTIVE는 추적 시작의 현재 상태이며 REMOVED 이외 값은 북마크가 있는 상태로 해석합니다.
 */
public enum MapBookmarkTrendEventType {
    BASELINE_ACTIVE,
    ADDED,
    REMOVED;

    public boolean isBookmarked() {
        return this != REMOVED;
    }
}
