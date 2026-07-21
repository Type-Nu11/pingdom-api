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
    CHECK_IN_NOT_FOUND(HttpStatus.NOT_FOUND, "증빙을 등록할 체크인을 찾을 수 없습니다."),
    VISIT_EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "방문 인증 증빙을 찾을 수 없습니다."),
    VISIT_EVIDENCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 체크인에 등록된 방문 인증 증빙이 이미 있습니다."),
    VISIT_EVIDENCE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "방문 인증 증빙 파일이 비어 있습니다."),
    VISIT_EVIDENCE_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "방문 인증 증빙 파일 크기가 허용 범위를 초과했습니다."),
    VISIT_EVIDENCE_FILE_INVALID(HttpStatus.BAD_REQUEST, "JPEG 또는 PNG 형식의 올바른 증빙 이미지만 등록할 수 있습니다."),
    VISIT_EVIDENCE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "증빙 저장소를 사용할 수 없습니다."),
    ACTIVE_REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 장소와 유형의 처리 중인 제보가 이미 있습니다."),
    INVALID_REPORT_DETAILS(HttpStatus.BAD_REQUEST, "제보 유형과 구조화 정보가 일치하지 않습니다."),
    INVALID_REPORT_STATE(HttpStatus.CONFLICT, "현재 제보 상태에서는 요청을 처리할 수 없습니다."),
    INVALID_REVIEW(HttpStatus.BAD_REQUEST, "심사 요청이 올바르지 않습니다."),
    CORRECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "방문자 검증 제보 정정을 찾을 수 없습니다."),
    CORRECTION_FORBIDDEN(HttpStatus.FORBIDDEN, "이 제보 정정을 조회할 권한이 없습니다."),
    CORRECTION_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 제보 상태에서는 정정을 제출할 수 없습니다."),
    ACTIVE_CORRECTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "처리 중인 제보 정정이 이미 있습니다."),
    INVALID_CORRECTION_DETAILS(HttpStatus.BAD_REQUEST, "제보 정정 내용이 올바르지 않습니다."),
    INVALID_CORRECTION_REVIEW(HttpStatus.BAD_REQUEST, "제보 정정 심사 요청이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
