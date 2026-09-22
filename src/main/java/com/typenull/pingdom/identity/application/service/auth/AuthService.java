package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;

/**
 * 가입·로그인·인증 복구·세션 회전·탈퇴의 애플리케이션 진입 계약.
 * 토큰을 HTTP 쿠키에 넣거나 제거하는 책임은 이 계약을 호출하는 API 계층에 있음.
 */
public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResult login(LoginRequest request);

    public LoginResult adminLogin(LoginRequest request);

    // 이메일 인증 메일 재발송 처리 메서드
    public void resendVerificationEmail(EmailResendRequest request);

    // 이메일 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request);

    // 비밀번호 재설정 토큰 발급 처리 메서드
    public void requestPasswordReset(PasswordResetRequest request);

    // 비밀번호 재설정 완료 처리 메서드
    public void confirmPasswordReset(PasswordResetConfirmRequest request);

    // Refresh Token 재발급 처리 메서드
    public TokenRefreshResult refreshToken(String refreshToken);

    // Refresh Token 무효화 기반 로그아웃 처리 메서드
    public void logout(String refreshToken);

    // 회원탈퇴 처리 메서드
    public void withdraw(Long userId);
}
