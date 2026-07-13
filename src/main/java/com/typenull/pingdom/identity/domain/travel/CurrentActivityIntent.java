package com.typenull.pingdom.identity.domain.travel;

/**
 * 사용자가 현재 탐색하려는 행동 범주입니다.
 *
 * <p>여행 목적은 장기 선호를, 이 값은 단기적인 장소 탐색 의도를 표현합니다.</p>
 */
public enum CurrentActivityIntent {
    EXPLORE,
    EAT,
    CAFE,
    SHOP,
    ATTEND_EVENT,
    NIGHTLIFE
}
