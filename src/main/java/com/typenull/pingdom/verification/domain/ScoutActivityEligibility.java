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
 * Scout 활동 자격의 상태와 적용 기간, 마지막 심사 이력을 보관한다.
 * 프로필 활성 여부와는 별도로 관리되며 version으로 동시 갱신을 감지한다.
 */
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

    /** 양수 Scout 사용자 ID로 아직 기간이 부여되지 않은 대기 자격을 생성한다. */
    public static ScoutActivityEligibility pending(Long scoutUserId, LocalDateTime now) {
        ScoutActivityEligibility eligibility = new ScoutActivityEligibility();
        eligibility.scoutUserId = requirePositive(scoutUserId, "scoutUserId");
        eligibility.status = ScoutActivityEligibilityStatus.PENDING;
        eligibility.createdAt = requireTime(now);
        eligibility.updatedAt = now;
        return eligibility;
    }

    /**
     * 대기 또는 만료 자격에 시작 시각을 포함하고 종료 시각을 제외하는 기간을 부여한다.
     * 종료 시각 null은 무기한을 뜻하며 유한 종료는 시작보다 뒤여야 한다. 이전 사유는 지운다.
     */
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

    /** ELIGIBLE 상태만 사유와 함께 정지한다. 기간 자체는 유지하며 활성 상태 판정으로 사용을 막는다. */
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

    /**
     * 유한 종료 시각에 도달한 ELIGIBLE 자격만 만료시킨다.
     * 무기한 자격과 아직 종료되지 않은 자격은 거부하며 심사자 정보는 갱신하지 않는다.
     */
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

    /** 활성 또는 정지 자격을 필수 사유와 함께 회수하고 마지막 심사 정보를 남긴다. */
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

    /**
     * ELIGIBLE 상태이며 시작 이상·종료 미만인 시각에만 true를 반환한다.
     * 종료가 null이면 상한을 적용하지 않으며 조회만으로 EXPIRED 상태로 변경하지 않는다.
     */
    public boolean isEligibleAt(LocalDateTime now) {
        LocalDateTime checkedAt = requireTime(now);
        return status == ScoutActivityEligibilityStatus.ELIGIBLE
                && eligibleFrom != null
                && !checkedAt.isBefore(eligibleFrom)
                && (eligibleUntil == null || checkedAt.isBefore(eligibleUntil));
    }

    /** 심사자·시각·사유와 수정 시각을 갱신한다. 활성화 시 사유 null을 허용한다. */
    private void review(Long adminUserId, String reason, LocalDateTime now) {
        reviewedByAdminUserId = requirePositive(adminUserId, "adminUserId");
        reviewedAt = requireTime(now);
        statusReason = reason;
        updatedAt = now;
    }

    /** 시작은 필수이며 종료가 주어지면 시작보다 엄격히 뒤여야 한다. */
    private static void requirePeriod(LocalDateTime from, LocalDateTime until) {
        if (from == null || (until != null && !until.isAfter(from))) {
            throw new IllegalArgumentException("eligible period is invalid");
        }
    }

    /** 누락되거나 양수가 아닌 사용자·심사자 식별자를 거부한다. */
    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /** 필수 사유의 null·공백 입력을 거부하고 앞뒤 공백을 제거해 반환한다. */
    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 기간 판정과 상태 변경에 사용할 시각의 null을 거부한다. */
    private static LocalDateTime requireTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("time must not be null");
        }
        return value;
    }
}
