package com.typenull.pingdom.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "scout_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoutProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 1000)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoutProfileStatus status;

    @Column(name = "reviewed_by_admin_user_id")
    private Long reviewedByAdminUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ScoutProfile pending(
            Long userId,
            String displayName,
            String introduction,
            LocalDateTime now
    ) {
        ScoutProfile profile = new ScoutProfile();
        profile.userId = requirePositive(userId, "userId");
        profile.displayName = requireText(displayName, "displayName");
        profile.introduction = normalize(introduction);
        profile.status = ScoutProfileStatus.PENDING;
        profile.createdAt = requireTime(now);
        profile.updatedAt = now;
        return profile;
    }

    public void updateProfile(String displayName, String introduction, LocalDateTime now) {
        if (status != ScoutProfileStatus.PENDING && status != ScoutProfileStatus.ACTIVE) {
            throw new IllegalStateException("현재 상태에서는 Scout 프로필을 수정할 수 없습니다.");
        }
        String normalizedDisplayName = requireText(displayName, "displayName");
        LocalDateTime changedAt = requireTime(now);
        this.displayName = normalizedDisplayName;
        this.introduction = normalize(introduction);
        this.updatedAt = changedAt;
    }

    public void activate(Long adminUserId, LocalDateTime now) {
        if (status != ScoutProfileStatus.PENDING && status != ScoutProfileStatus.SUSPENDED) {
            throw new IllegalStateException("대기 또는 정지 상태의 Scout 프로필만 활성화할 수 있습니다.");
        }
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutProfileStatus.ACTIVE;
        review(reviewer, null, reviewedAt);
    }

    public void suspend(Long adminUserId, String reason, LocalDateTime now) {
        if (status != ScoutProfileStatus.ACTIVE) {
            throw new IllegalStateException("활성 Scout 프로필만 정지할 수 있습니다.");
        }
        String normalizedReason = requireText(reason, "reason");
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutProfileStatus.SUSPENDED;
        review(reviewer, normalizedReason, reviewedAt);
    }

    public void revoke(Long adminUserId, String reason, LocalDateTime now) {
        if (status != ScoutProfileStatus.ACTIVE && status != ScoutProfileStatus.SUSPENDED) {
            throw new IllegalStateException("활성 또는 정지 상태의 Scout 프로필만 회수할 수 있습니다.");
        }
        String normalizedReason = requireText(reason, "reason");
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutProfileStatus.REVOKED;
        review(reviewer, normalizedReason, reviewedAt);
    }

    private void review(Long adminUserId, String reason, LocalDateTime now) {
        reviewedByAdminUserId = requirePositive(adminUserId, "adminUserId");
        reviewedAt = requireTime(now);
        statusReason = reason;
        updatedAt = now;
    }

    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDateTime requireTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        return value;
    }
}
