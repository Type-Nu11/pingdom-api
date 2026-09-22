package com.typenull.pingdom.shared.exception;

/** 장소 관련 오류 코드를 공통 예외 처리기로 전달하는 도메인 예외. */
public class MapException extends DomainException {

    public MapException(MapErrorCode errorCode) {
        super(errorCode);
    }
}
