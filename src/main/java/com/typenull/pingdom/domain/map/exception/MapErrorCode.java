package com.typenull.pingdom.domain.map.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MapErrorCode {
    Image_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    OTHERS_NOT_DELETED(HttpStatus.FORBIDDEN,"자신의 사진만 삭제할 수 있습니다."),
    DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"이미지를 삭제하는 데 실패했습니다. 잠시 후 다시 시도해 주세요."),
    S3_CONNECTION_ERROR(HttpStatus.BAD_REQUEST,"사용자의 네트워크 오류");

    private final HttpStatus status;
    private final String message;
}
