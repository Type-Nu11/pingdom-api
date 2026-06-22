package com.typenull.pingdom.identity.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    USER_BANNED(HttpStatus.FORBIDDEN, "밴 처리된 사용자입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴 처리된 사용자입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 올바르지 않습니다."),
    EXPIRED_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 만료되었습니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 이메일 인증이 완료된 사용자입니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
