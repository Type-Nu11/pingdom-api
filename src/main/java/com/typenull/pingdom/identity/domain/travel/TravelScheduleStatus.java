package com.typenull.pingdom.identity.domain.travel;

/**
 * 기준일과 취소 여부로 계산하는 화면 표시 상태. 이 값의 별도 저장 상태 갱신은 미수행.
 */
public enum TravelScheduleStatus {
    UPCOMING,
    ONGOING,
    ENDED,
    CANCELLED
}
