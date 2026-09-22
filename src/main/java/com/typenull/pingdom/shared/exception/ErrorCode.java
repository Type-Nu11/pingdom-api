package com.typenull.pingdom.shared.exception;

import org.springframework.http.HttpStatus;

/** HTTP 상태·공개 메시지·기계 판독 코드를 제공한다. 기본 getCode 구현은 구현체가 enum이라는 전제를 가진다. */
public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    default String getCode() {
        return ((Enum<?>) this).name();
    }
}
