package com.typenull.pingdom.identity.domain.travel;

/**
 * 기준일과 취소 여부로 계산하는 화면 표시 상태입니다. 이 값을 별도 저장 상태로 갱신하지 않습니다.
 */
public enum TravelScheduleStatus {
    UPCOMING,
    ONGOING,
    ENDED,
    CANCELLED
}
