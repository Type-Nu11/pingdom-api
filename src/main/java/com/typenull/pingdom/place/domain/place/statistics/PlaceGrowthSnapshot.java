package com.typenull.pingdom.place.domain.place.statistics;

/**
 * 사진 수로 계산한 현재 레벨·현재/다음 레벨 최소 사진 수·정수 진행률입니다.
 * 다음 단계 경계가 Long.MAX_VALUE인 마지막 구간은 진행률 100으로 표시합니다.
 */
public record PlaceGrowthSnapshot(
        long photoCount,
        int level,
        long currentLevelMinPhotoCount,
        long nextLevelMinPhotoCount,
        int progressPercent
) {
}
