package com.typenull.pingdom.domain.users.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UsersException extends RuntimeException {

    private final UsersErrorCode errorCode;

  public UsersException(UsersErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public HttpStatus getStatus() {
    return errorCode.getStatus();
  }

}
