package com.typenull.pingdom.shared.exception;

public class MapException extends DomainException {

    public MapException(MapErrorCode errorCode) {
        super(errorCode);
    }
}
