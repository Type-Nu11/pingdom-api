package com.typenull.pingdom.place.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceRegistrationErrorCode implements ErrorCode {
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 등록 신청을 찾을 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 장소 등록 신청에 접근할 수 없습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 신청 상태에서는 요청을 처리할 수 없습니다."),
    STALE_REVIEW_VERSION(HttpStatus.CONFLICT, "심사 대상이 변경되어 최신 정보를 다시 확인해야 합니다."),
    REQUIRED_FILES_MISSING(HttpStatus.BAD_REQUEST, "필수 장소 등록 파일이 누락되었습니다."),
    INVALID_ATTACHMENT_METADATA(HttpStatus.BAD_REQUEST, "장소 등록 첨부 파일 정보가 올바르지 않습니다."),
    ATTACHMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "장소 등록 첨부 파일 크기가 제한을 초과했습니다."),
    DUPLICATE_ATTACHMENT(HttpStatus.CONFLICT, "동일한 장소 등록 첨부 파일이 이미 존재합니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 등록 첨부 파일을 찾을 수 없습니다."),
    ATTACHMENT_RETENTION_EXPIRED(HttpStatus.GONE, "첨부 파일 보존 기한이 만료되었습니다."),
    DUPLICATE_PLACE(HttpStatus.CONFLICT, "이미 등록된 장소이거나 중복 신청입니다."),
    MERCHANT_PROFILE_REQUIRED(HttpStatus.CONFLICT, "Merchant Owner 프로필이 필요합니다.");
    private final HttpStatus status;
    private final String message;
}
