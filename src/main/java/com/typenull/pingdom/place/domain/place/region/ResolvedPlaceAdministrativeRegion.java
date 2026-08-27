package com.typenull.pingdom.place.domain.place.region;

import org.springframework.util.StringUtils;

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
