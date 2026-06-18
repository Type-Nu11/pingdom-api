package com.typenull.pingdom.place.domain.place;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlaceLevelPolicy {

    private static final long BASE_REQUIRED_PHOTO_COUNT = 1L;
    private static final double GROWTH_FACTOR = 1.55d;
    private static final long[] LEVEL_THRESHOLDS = buildThresholds();

    private PlaceLevelPolicy() {
    }

    public static PlaceGrowthSnapshot snapshot(long photoCount) {
        long normalizedPhotoCount = Math.max(0L, photoCount);
        int thresholdIndex = findThresholdIndex(normalizedPhotoCount);
        int level = thresholdIndex + 1;
        long currentLevelMinPhotoCount = LEVEL_THRESHOLDS[thresholdIndex];
        long nextLevelMinPhotoCount = thresholdIndex + 1 < LEVEL_THRESHOLDS.length
                ? LEVEL_THRESHOLDS[thresholdIndex + 1]
                : Long.MAX_VALUE;

        return new PlaceGrowthSnapshot(
                normalizedPhotoCount,
                level,
                currentLevelMinPhotoCount,
                nextLevelMinPhotoCount,
                calculateProgressPercent(normalizedPhotoCount, currentLevelMinPhotoCount, nextLevelMinPhotoCount)
        );
    }

    private static int findThresholdIndex(long photoCount) {
        int rawIndex = Arrays.binarySearch(LEVEL_THRESHOLDS, photoCount);
        if (rawIndex >= 0) {
            return rawIndex;
        }
        return Math.max(0, -rawIndex - 2);
    }

    private static int calculateProgressPercent(
            long photoCount,
            long currentLevelMinPhotoCount,
            long nextLevelMinPhotoCount
    ) {
        if (nextLevelMinPhotoCount == Long.MAX_VALUE) {
            return 100;
        }

        long requiredSpan = nextLevelMinPhotoCount - currentLevelMinPhotoCount;
        if (requiredSpan <= 0L) {
            return 100;
        }

        long progressed = photoCount - currentLevelMinPhotoCount;
        double progress = ((double) progressed * 100d) / (double) requiredSpan;
        return (int) Math.min(99d, Math.floor(progress));
    }

    private static long[] buildThresholds() {
        List<Long> thresholds = new ArrayList<>();
        thresholds.add(0L);

        long accumulated = 0L;
        double requiredForNextLevel = BASE_REQUIRED_PHOTO_COUNT;

        while (requiredForNextLevel < Long.MAX_VALUE) {
            long delta = Math.max(1L, (long) Math.ceil(requiredForNextLevel));
            if (Long.MAX_VALUE - accumulated < delta) {
                break;
            }

            accumulated += delta;
            thresholds.add(accumulated);
            requiredForNextLevel *= GROWTH_FACTOR;
        }

        return thresholds.stream().mapToLong(Long::longValue).toArray();
    }
}
