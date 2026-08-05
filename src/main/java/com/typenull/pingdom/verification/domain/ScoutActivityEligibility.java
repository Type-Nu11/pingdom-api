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
@Table(name = "scout_activity_eligibility")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoutActivityEligibility {

    @Id
    @Column(name = "scout_user_id")
    private Long scoutUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoutActivityEligibilityStatus status;

    @Column(name = "eligible_from")
    private LocalDateTime eligibleFrom;

    @Column(name = "eligible_until")
    private LocalDateTime eligibleUntil;

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

    public static ScoutActivityEligibility pending(Long scoutUserId, LocalDateTime now) {
        ScoutActivityEligibility eligibility = new ScoutActivityEligibility();
        eligibility.scoutUserId = requirePositive(scoutUserId, "scoutUserId");
        eligibility.status = ScoutActivityEligibilityStatus.PENDING;
        eligibility.createdAt = requireTime(now);
        eligibility.updatedAt = now;
        return eligibility;
    }

    public void grant(
            Long adminUserId,
            LocalDateTime eligibleFrom,
            LocalDateTime eligibleUntil,
            LocalDateTime now
    ) {
        if (status != ScoutActivityEligibilityStatus.PENDING
                && status != ScoutActivityEligibilityStatus.EXPIRED) {
            throw new IllegalStateException("대기 또는 만료 상태의 Scout 활동 자격만 부여할 수 있습니다.");
        }
        requirePeriod(eligibleFrom, eligibleUntil);
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutActivityEligibilityStatus.ELIGIBLE;
        this.eligibleFrom = eligibleFrom;
        this.eligibleUntil = eligibleUntil;
        review(reviewer, null, reviewedAt);
    }

    public void suspend(Long adminUserId, String reason, LocalDateTime now) {
        if (status != ScoutActivityEligibilityStatus.ELIGIBLE) {
            throw new IllegalStateException("활성 Scout 활동 자격만 정지할 수 있습니다.");
        }
        String normalizedReason = requireText(reason, "reason");
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutActivityEligibilityStatus.SUSPENDED;
        review(reviewer, normalizedReason, reviewedAt);
    }

    public void expire(LocalDateTime now) {
        LocalDateTime expiredAt = requireTime(now);
        if (status != ScoutActivityEligibilityStatus.ELIGIBLE
                || eligibleUntil == null
                || expiredAt.isBefore(eligibleUntil)) {
            throw new IllegalStateException("만료 시각이 지난 활성 Scout 활동 자격만 만료할 수 있습니다.");
        }
        status = ScoutActivityEligibilityStatus.EXPIRED;
        updatedAt = expiredAt;
    }

    public void revoke(Long adminUserId, String reason, LocalDateTime now) {
        if (status != ScoutActivityEligibilityStatus.ELIGIBLE
                && status != ScoutActivityEligibilityStatus.SUSPENDED) {
            throw new IllegalStateException("활성 또는 정지 상태의 Scout 활동 자격만 회수할 수 있습니다.");
        }
        String normalizedReason = requireText(reason, "reason");
        Long reviewer = requirePositive(adminUserId, "adminUserId");
        LocalDateTime reviewedAt = requireTime(now);
        status = ScoutActivityEligibilityStatus.REVOKED;
        review(reviewer, normalizedReason, reviewedAt);
    }

    public boolean isEligibleAt(LocalDateTime now) {
        LocalDateTime checkedAt = requireTime(now);
        return status == ScoutActivityEligibilityStatus.ELIGIBLE
                && eligibleFrom != null
                && !checkedAt.isBefore(eligibleFrom)
                && (eligibleUntil == null || checkedAt.isBefore(eligibleUntil));
    }

    private void review(Long adminUserId, String reason, LocalDateTime now) {
        reviewedByAdminUserId = requirePositive(adminUserId, "adminUserId");
        reviewedAt = requireTime(now);
        statusReason = reason;
        updatedAt = now;
    }

    private static void requirePeriod(LocalDateTime from, LocalDateTime until) {
        if (from == null || (until != null && !until.isAfter(from))) {
            throw new IllegalArgumentException("eligible period is invalid");
        }
    }

    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static LocalDateTime requireTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        return value;
    }
}
