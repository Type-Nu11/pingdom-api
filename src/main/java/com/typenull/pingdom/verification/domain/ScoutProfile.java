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

/**
 * Scout 프로필의 심사 상태와 표시 정보, 마지막 심사 이력을 관리.
 * 프로필 상태 전이만 담당하며 활동 자격 기간은 ScoutActivityEligibility에서 별도로 판단.
 * version은 JPA 낙관적 잠금에 사용됨.
 */
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

    /**
     * 양수 사용자 ID와 비어 있지 않은 표시 이름으로 심사 대기 프로필을 생성.
     * 이름·소개는 앞뒤 공백을 제거하고 빈 소개는 null로 보관.
     */
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

    /** 대기 또는 활성 상태에서만 표시 정보를 수정. 입력과 시각을 먼저 검증한 뒤 필드를 변경. */
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

    /** 대기 또는 정지 프로필을 활성화하고 심사자·시각을 갱신. 이전 정지 사유는 삭제. */
    public void activate(Long adminUserId, LocalDateTime now) {
        if (status != ScoutProfileStatus.PENDING && status != ScoutProfileStatus.SUSPENDED) {
            throw new IllegalStateException("대기 또는 정지 상태의 Scout 프로필만 활성화할 수 있습니다.");
        }
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutProfileStatus.ACTIVE;
        review(reviewer, null, reviewedAt);
    }

    /** 활성 프로필에 한해 필수 사유를 기록하고 정지. 유효하지 않은 사유·심사자·시각은 변경 전에 거부. */
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

    /** 활성 또는 정지 프로필만 사유와 함께 회수. 대기 및 이미 회수된 상태는 회수 대상에서 제외. */
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

    /** 마지막 심사자·시각·사유를 기록하고 수정 시각을 함께 갱신. */
    private void review(Long adminUserId, String reason, LocalDateTime now) {
        reviewedByAdminUserId = requirePositive(adminUserId, "adminUserId");
        reviewedAt = requireTime(now);
        statusReason = reason;
        updatedAt = now;
    }

    /** 필수 식별자가 양수인지 확인하며 실패 메시지에 인자 이름을 포함. */
    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /** 앞뒤 공백을 제거한 문자열을 반환하고 null 또는 공백뿐인 필수 값은 거부. */
    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    /** 선택 문자열의 공백을 정리하고 null 또는 빈 값을 null로 통일. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 상태 변경 이력을 남길 시각이 반드시 제공되도록 null을 거부. */
    private static LocalDateTime requireTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        return value;
    }
}
