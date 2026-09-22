package com.typenull.pingdom.identity.domain.travel;

/**
 * DB에 저장하는 일정 생명주기 상태입니다. 날짜에 따른 예정·진행·종료 표시는 TravelScheduleStatus로 계산합니다.
 */
public enum TravelScheduleState {
    SCHEDULED,
    CANCELLED
}
