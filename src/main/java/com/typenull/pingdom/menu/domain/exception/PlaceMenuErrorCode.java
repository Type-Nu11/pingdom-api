package com.typenull.pingdom.menu.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceMenuErrorCode implements ErrorCode {
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 메뉴를 찾을 수 없습니다."),
    MENU_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    MENU_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 장소의 메뉴를 관리할 권한이 없습니다."),
    INVALID_MENU_INPUT(HttpStatus.BAD_REQUEST, "장소 메뉴 요청이 올바르지 않습니다."),
    MENU_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 메뉴 상태에서는 요청을 처리할 수 없습니다."),
    MENU_ORDER_CONFLICT(HttpStatus.CONFLICT, "메뉴 표시 순서가 변경되었습니다.");

    private final HttpStatus status;
    private final String message;
}
