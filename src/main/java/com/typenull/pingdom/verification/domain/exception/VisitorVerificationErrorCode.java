package com.typenull.pingdom.verification.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VisitorVerificationErrorCode {
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "방문자 검증 제보를 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "이 제보를 조회할 권한이 없습니다."),
    ADMIN_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "활성 관리자 계정만 제보를 심사할 수 있습니다."),
    TOURIST_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "활성 일반 사용자만 제보할 수 있습니다."),
    LOCATION_OBSERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "위치 측정 시각이 유효 범위를 벗어났습니다."),
    LOCATION_TOO_INACCURATE(HttpStatus.BAD_REQUEST, "위치 정확도가 체크인 기준을 충족하지 않습니다."),
    OUTSIDE_CHECK_IN_RADIUS(HttpStatus.UNPROCESSABLE_ENTITY, "장소의 체크인 허용 반경 밖입니다."),
    DAILY_CHECK_IN_ALREADY_EXISTS(HttpStatus.CONFLICT, "오늘 이 장소에서 완료한 체크인이 이미 있습니다."),
    ACTIVE_REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 장소와 유형의 처리 중인 제보가 이미 있습니다."),
    INVALID_REPORT_STATE(HttpStatus.CONFLICT, "현재 제보 상태에서는 요청을 처리할 수 없습니다."),
    INVALID_REVIEW(HttpStatus.BAD_REQUEST, "심사 요청이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
