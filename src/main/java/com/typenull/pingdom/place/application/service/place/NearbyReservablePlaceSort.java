package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 주변 예약 가능 장소의 정렬 선택값. 공백·미입력은 NEAREST이며 미지원 값은 검색 조건 오류로 처리.
 */
public enum NearbyReservablePlaceSort {
    NEAREST,
    EARLIEST_AVAILABLE,
    POPULAR;

    public static NearbyReservablePlaceSort from(String value) {
        if (!StringUtils.hasText(value)) return NEAREST;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
    }
}
