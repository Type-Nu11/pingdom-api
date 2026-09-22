package com.typenull.pingdom.place.domain.place.region;

import org.springframework.util.StringUtils;

/**
 * 좌표 해석 결과의 5자리 지역 코드와 지역명.
 * 코드 형식은 trim 이전에 검사하므로 공백이 포함된 코드는 유효한 코드로 보정 불가.
 */
public record ResolvedPlaceAdministrativeRegion(
        String code,
        String sido,
        String sigungu,
        String regionName
) {

    public ResolvedPlaceAdministrativeRegion {
        if (!StringUtils.hasText(code) || !code.matches("\\d{5}")
                || !StringUtils.hasText(sido) || !StringUtils.hasText(sigungu) || !StringUtils.hasText(regionName)) {
            throw new IllegalArgumentException("유효한 시·군·구 행정구역 정보가 필요합니다.");
        }
        code = code.trim();
        sido = sido.trim();
        sigungu = sigungu.trim();
        regionName = regionName.trim();
    }
}
