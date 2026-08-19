package com.typenull.pingdom.notification.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationsErrorCode implements ErrorCode {
    FCM_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "작성자의 FCM아이디를 찾을 수 없습니다,"),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "알림 전송에 실패했습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    CANNOT_SEND_NOTIFICATION_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 알림을 보낼 수 없습니다."),
    INVALID_FCM_TOKEN(HttpStatus.BAD_REQUEST, "FCM 토큰을 입력해주세요."),
    INVALID_NOTIFICATION_TIMEZONE(HttpStatus.BAD_REQUEST, "유효하지 않은 timezone입니다."),
    INVALID_QUIET_HOURS(HttpStatus.BAD_REQUEST, "quiet hours 설정을 확인해주세요.");


    private final HttpStatus status;
    private final String message;
}
