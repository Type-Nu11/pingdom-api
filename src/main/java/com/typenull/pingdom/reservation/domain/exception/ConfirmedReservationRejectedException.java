package com.typenull.pingdom.reservation.domain.exception;

/** 재고 변경 전에 확정한 거절만 사용. 호출 트랜잭션은 거절 영수증을 커밋한 뒤 HTTP 오류를 반환. */
public class ConfirmedReservationRejectedException extends ReservationException {
    public ConfirmedReservationRejectedException(ReservationErrorCode code) { super(code); }
}
