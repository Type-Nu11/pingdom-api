package com.typenull.pingdom.place.domain.place.statistics;

public record PlaceGrowthSnapshot(
        long photoCount,
        int level,
        long currentLevelMinPhotoCount,
        long nextLevelMinPhotoCount,
        int progressPercent
) {
}
