package com.typenull.pingdom.domain.admin.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode {
    PICTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다."),
    PICTURE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사진 삭제에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}

