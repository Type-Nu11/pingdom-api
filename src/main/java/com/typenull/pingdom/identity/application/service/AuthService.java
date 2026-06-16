package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);

    public LoginResponse adminLogin(LoginRequest request);

    // 이메일 인증 메일 재발송 처리 메서드
    public void resendVerificationEmail(EmailResendRequest request);

    // 이메일 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request);

    // Refresh Token 재발급 처리 메서드
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    // Refresh Token 무효화 기반 로그아웃 처리 메서드
    public void logout(RefreshTokenRequest request);

    // 회원탈퇴 처리 메서드
    public void withdraw(Long userId);
}
