package com.typenull.pingdom.notification.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationsErrorCode {
    FCM_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "작성자의 FCM아이디를 찾을 수 없습니다,"),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "알림 전송에 실패했습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알람을 찾을 수 없습니다,");


    private final HttpStatus status;
    private final String message;
}
