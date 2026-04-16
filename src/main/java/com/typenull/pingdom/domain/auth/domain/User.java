package com.typenull.pingdom.domain.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.*;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    // 이메일 인증 연계용 메일 주소
    @Column(length = 255)
    private String email;

    // 이메일 인증 완료 상태
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    // 이메일 인증 코드 저장 필드
    @Column(length = 20)
    private String emailVerificationCode;

    // 이메일 인증 코드 만료 시각 필드
    private LocalDateTime emailVerificationExpiresAt;

    @Column(nullable = false)
    private String password;

    // 현재 활성 Refresh Token 저장 필드
    @Column(length = 1000)
    private String refreshToken;

    // 이메일 인증 코드 발급 메서드
    public void issueEmailVerification(String verificationCode, LocalDateTime expiresAt) {
        this.emailVerificationCode = verificationCode;
        this.emailVerificationExpiresAt = expiresAt;
        this.emailVerified = false;
    }

    // 이메일 인증 코드 일치 여부 확인 메서드
    public boolean matchesEmailVerificationCode(String verificationCode) {
        return Objects.equals(this.emailVerificationCode, verificationCode);
    }

    // 이메일 인증 코드 만료 여부 확인 메서드
    public boolean isEmailVerificationExpired(LocalDateTime now) {
        return this.emailVerificationExpiresAt == null || now.isAfter(this.emailVerificationExpiresAt);
    }

    // 이메일 인증 완료 처리 메서드
    public void verifyEmail() {
        this.emailVerified = true;
        this.emailVerificationCode = null;
        this.emailVerificationExpiresAt = null;
    }

    // Refresh Token 발급 상태 반영 메서드
    public void issueRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // Refresh Token 일치 여부 확인 메서드
    public boolean matchesRefreshToken(String refreshToken) {
        return Objects.equals(this.refreshToken, refreshToken);
    }

    // Refresh Token 제거 메서드
    public void clearRefreshToken() {
        this.refreshToken = null;
    }


    public void changePassword(String password) {
        this.password = password;
    }

    public void changeUsername(String username) {
        this.username = username;
    }
}
