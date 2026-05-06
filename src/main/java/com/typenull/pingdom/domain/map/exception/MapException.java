package com.typenull.pingdom.domain.map.exception;

import com.typenull.pingdom.domain.users.exception.UsersErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MapException extends RuntimeException {

    private final MapErrorCode errorCode;

    public MapException(MapErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

}
