package com.typenull.pingdom.domain.auth.service;

import com.typenull.pingdom.domain.auth.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.signup.UserResponse;
import com.typenull.pingdom.domain.auth.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.domain.auth.dto.token.RefreshTokenResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);

    // 이메일 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request);

    // Refresh Token 재발급 처리 메서드
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    // 회원탈퇴 처리 메서드
    public void withdraw(Long userId);
}
