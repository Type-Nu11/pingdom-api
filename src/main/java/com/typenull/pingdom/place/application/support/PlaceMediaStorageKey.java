package com.typenull.pingdom.place.application.support;

import java.util.UUID;
import org.springframework.util.StringUtils;

/** 상점주가 발급받는 장소 탐색 미디어 S3 key의 소유 범위를 정의합니다. */
public final class PlaceMediaStorageKey {

    private PlaceMediaStorageKey() {
    }

    public static String createExplorationKey(Long placeId, Long userId, String extension) {
        return explorationPrefix(placeId, userId) + UUID.randomUUID() + "." + extension;
    }

    public static boolean belongsToExplorationMedia(String key, Long placeId, Long userId) {
        return StringUtils.hasText(key) && key.trim().startsWith(explorationPrefix(placeId, userId));
    }

    /** 승인 신청 첨부의 공개 복구 키입니다. attachmentId를 포함해 재시도해도 같은 객체만 갱신합니다. */
    public static String createRegistrationExplorationKey(
            Long placeId,
            Long userId,
            Long attachmentId,
            String extension
    ) {
        return explorationPrefix(placeId, userId)
                + "registration/" + attachmentId + "." + extension;
    }

    private static String explorationPrefix(Long placeId, Long userId) {
        return "places/%d/exploration/%d/".formatted(placeId, userId);
    }
}
