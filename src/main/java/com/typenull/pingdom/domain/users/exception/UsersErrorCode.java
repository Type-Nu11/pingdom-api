package com.typenull.pingdom.domain.users.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UsersErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,"비밀번호가 서로 다릅니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT,"이미 있는 아이디입니다.");

    private final HttpStatus status;
    private final String message;
}
