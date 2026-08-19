package com.typenull.pingdom.identity.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    USER_BANNED(HttpStatus.FORBIDDEN, "밴 처리된 사용자입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴 처리된 사용자입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 토큰이 올바르지 않습니다."),
    EXPIRED_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 토큰이 만료되었습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 서로 다릅니다."),
    INVALID_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 올바르지 않습니다."),
    EXPIRED_EMAIL_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "이메일 인증 코드가 만료되었습니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 이메일 인증이 완료된 사용자입니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    OAUTH_EMAIL_CONFLICT(HttpStatus.CONFLICT, "이미 로컬 계정으로 가입된 이메일입니다."),
    OAUTH_EMAIL_MISMATCH(HttpStatus.CONFLICT, "현재 계정 이메일과 OAuth 계정 이메일이 일치하지 않습니다."),
    OAUTH_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 OAuth 계정입니다."),
    OAUTH_ACCOUNT_NOT_LINKED(HttpStatus.NOT_FOUND, "연결된 OAuth 계정을 찾을 수 없습니다."),
    OAUTH_LINK_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "OAuth 계정 연결 요청이 유효하지 않습니다."),
    OAUTH_PASSWORD_CONFIRMATION_REQUIRED(HttpStatus.CONFLICT, "OAuth 계정 해제를 위해 현재 비밀번호 확인이 필요합니다."),
    OAUTH_LOCAL_PASSWORD_REQUIRED(HttpStatus.CONFLICT, "OAuth 계정 해제 전 로컬 비밀번호를 먼저 설정해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
