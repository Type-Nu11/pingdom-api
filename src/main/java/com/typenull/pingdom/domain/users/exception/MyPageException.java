package com.typenull.pingdom.domain.users.exception;

import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MyPageException extends RuntimeException {

    private final MyPageErrorCode errorCode;

  public MyPageException(MyPageErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public HttpStatus getStatus() {
    return errorCode.getStatus();
  }

}
