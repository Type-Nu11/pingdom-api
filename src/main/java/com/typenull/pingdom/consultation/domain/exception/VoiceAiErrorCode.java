package com.typenull.pingdom.consultation.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VoiceAiErrorCode implements ErrorCode {
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "음성 AI 세션을 찾을 수 없습니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 사용자의 음성 AI 세션에는 접근할 수 없습니다."),
    SESSION_EXPIRED(HttpStatus.GONE, "음성 AI 세션이 만료되었거나 종료되었습니다."),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI provider를 사용할 수 없습니다."),
    PROVIDER_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI provider 응답이 계약에 맞지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
