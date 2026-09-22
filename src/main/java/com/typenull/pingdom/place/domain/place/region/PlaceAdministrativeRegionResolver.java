package com.typenull.pingdom.place.domain.place.region;

/**
 * 좌표를 시·군·구 정보로 해석하는 외부 제공자 포트입니다.
 * 호출자는 isConfigured로 사용 가능 여부를 확인할 수 있으며 resolve의 실패 정책은 구현을 따릅니다.
 */
public interface PlaceAdministrativeRegionResolver {

    boolean isConfigured();

    ResolvedPlaceAdministrativeRegion resolve(double latitude, double longitude);
}
