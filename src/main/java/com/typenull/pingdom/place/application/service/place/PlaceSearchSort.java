package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 일반 장소 검색 정렬값입니다. 공백·미입력은 LATEST이며 알 수 없는 값에는 전용 정렬 오류를 반환합니다.
 */
public enum PlaceSearchSort {
    LATEST,
    NEAREST,
    POPULAR;

    /**
     * null·공백은 LATEST로 처리하고 나머지는 공백 제거·대문자 변환 후 정렬 값으로 해석합니다.
     * 지원하지 않는 이름은 UNSUPPORTED_PLACE_SEARCH_SORT로 거절합니다.
     */
    public static PlaceSearchSort from(String value) {
        if (!StringUtils.hasText(value)) {
            return LATEST;
        }

        try {
            return PlaceSearchSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.UNSUPPORTED_PLACE_SEARCH_SORT);
        }
    }
}
