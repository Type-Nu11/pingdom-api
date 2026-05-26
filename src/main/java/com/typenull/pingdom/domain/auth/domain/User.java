package com.typenull.pingdom.domain.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // 이메일 인증 연계용 메일 주소
    @Column(nullable = false, unique = true, length = 255)
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

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(length = 20)
    private String language;

    @Column(length = 100)
    private String country;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 현재 활성 Refresh Token 저장 필드
    @Column(length = 1000)
    private String refreshToken;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    // 관리자 밴 여부
    @Builder.Default
    @Column(nullable = false)
    private boolean banned = false;

    // 밴 처리 시각
    private LocalDateTime bannedAt;

    // 밴 사유
    @Column(length = 255)
    private String banReason;

    // fcm 디바이스 아이디
    private String fcmToken;

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

    public void ban(String reason, LocalDateTime now) {
        this.banned = true;
        this.bannedAt = now;
        this.banReason = reason;
        // 밴되면 기존 리프레시 토큰도 무효화
        this.refreshToken = null;
    }


    public void changePassword(String password) {
        this.password = password;
    }

    public void changeUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }
}
