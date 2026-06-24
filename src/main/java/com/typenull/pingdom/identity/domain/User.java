package com.typenull.pingdom.identity.domain;

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

    public static final String WITHDRAWN_DISPLAY_NAME = "탈퇴 사용자";

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

    @Column(name = "birth_year", nullable = false)
    private Integer birthYear;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(nullable = false, length = 100)
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Builder.Default
    @Column(name = "unaccepted_report_count")
    private Long unacceptedReportCount = 0L;

    @Builder.Default
    @Column(name = "report_count")
    private Long reportCount = 0L;

    // 관리자 밴 여부
    @Builder.Default
    @Column(nullable = false)
    private boolean banned = false;

    // 밴 처리 시각
    private LocalDateTime bannedAt;

    // 밴 사유
    @Column(length = 255)
    private String banReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ban_type", length = 20)
    private UserBanType banType;

    @Column(name = "ban_expires_at")
    private LocalDateTime banExpiresAt;

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

    // 총 신고 횟수
    public void increaseReportCount(){
        this.reportCount += 1;
    }

    // 기각 신고 횟수 증가
    public void increaseUnacceptedReportCount(){
        this.unacceptedReportCount += 1;
    }

    public Long getUnacceptedReportPercent() {
        if(reportCount == null || reportCount == 0)
        {
            return 0L;
        }
        return (unacceptedReportCount * 100) / reportCount;
    }
    public void ban(String reason, LocalDateTime now) {
        ban(reason, now, null);
    }

    public void ban(String reason, LocalDateTime now, LocalDateTime expiresAt) {
        this.banned = true;
        this.bannedAt = now;
        this.banReason = reason;
        this.banType = expiresAt == null ? UserBanType.PERMANENT : UserBanType.TEMPORARY;
        this.banExpiresAt = expiresAt;
        // 밴되면 기존 리프레시 토큰도 무효화
        this.refreshToken = null;
    }

    public void releaseBan() {
        this.banned = false;
        this.bannedAt = null;
        this.banReason = null;
        this.banType = null;
        this.banExpiresAt = null;
    }

    public boolean isCurrentlyBanned(LocalDateTime now) {
        return this.banned && !isBanExpired(now);
    }

    public boolean isBanExpired(LocalDateTime now) {
        return this.banned
                && this.banType == UserBanType.TEMPORARY
                && this.banExpiresAt != null
                && !this.banExpiresAt.isAfter(now);
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

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }

    public void withdraw(String anonymizedUsername, String anonymizedEmail, String anonymizedPassword, LocalDateTime now) {
        if (isWithdrawn()) {
            return;
        }

        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.username = anonymizedUsername;
        this.email = anonymizedEmail;
        this.password = anonymizedPassword;
        this.birthYear = 0;
        this.profileImageUrl = null;
        this.language = "und";
        this.country = "UNKNOWN";
        this.emailVerified = false;
        this.emailVerificationCode = null;
        this.emailVerificationExpiresAt = null;
        this.refreshToken = null;
        this.fcmToken = null;
        releaseBan();
    }
}
