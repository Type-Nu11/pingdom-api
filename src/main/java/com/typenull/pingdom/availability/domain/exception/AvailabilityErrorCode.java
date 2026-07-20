package com.typenull.pingdom.availability.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AvailabilityErrorCode {
    AVAILABILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 가능 시간을 찾을 수 없습니다."),
    PLACE_NOT_OWNED(HttpStatus.FORBIDDEN, "소유한 장소의 예약 가능 시간만 관리할 수 있습니다."),
    AVAILABILITY_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일한 예약 가능 시간이 이미 존재합니다."),
    INVALID_AVAILABILITY_INPUT(HttpStatus.BAD_REQUEST, "예약 가능 시간 입력값이 올바르지 않습니다."),
    INVALID_AVAILABILITY_STATE(HttpStatus.CONFLICT, "현재 예약 가능 시간 상태에서는 요청을 처리할 수 없습니다."),
    AVAILABILITY_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원이 부족합니다.");

    private final HttpStatus status;
    private final String message;
}
