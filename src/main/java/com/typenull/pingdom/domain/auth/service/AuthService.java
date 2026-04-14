package com.typenull.pingdom.domain.auth.service;

import com.typenull.pingdom.domain.auth.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.signup.UserResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);

    // 이메일 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request);
}
