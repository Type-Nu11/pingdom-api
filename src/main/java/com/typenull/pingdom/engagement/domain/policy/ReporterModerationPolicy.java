package com.typenull.pingdom.engagement.domain.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reporter_moderation_policy")
public class ReporterModerationPolicy {

    private static final int DEFAULT_TRUST_SCORE = 100;
    private static final int MAX_TRUST_SCORE = 100;
    private static final int MIN_TRUST_SCORE = 0;

    @Id
    @Column(name = "reporter_user_id")
    private Long reporterUserId;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    @Builder.Default
    @Column(name = "submitted_count", nullable = false)
    private long submittedCount = 0L;

    @Builder.Default
    @Column(name = "accepted_count", nullable = false)
    private long acceptedCount = 0L;

    @Builder.Default
    @Column(name = "declined_count", nullable = false)
    private long declinedCount = 0L;

    @Builder.Default
    @Column(name = "false_report_count", nullable = false)
    private long falseReportCount = 0L;

    @Builder.Default
    @Column(name = "trust_score", nullable = false)
    private int trustScore = DEFAULT_TRUST_SCORE;

    @Column(name = "restricted_until")
    private LocalDateTime restrictedUntil;

    @Column(name = "restriction_reason", length = 500)
    private String restrictionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ReporterModerationPolicy create(Long reporterUserId, String reporterUsername) {
        return ReporterModerationPolicy.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername(reporterUsername)
                .trustScore(DEFAULT_TRUST_SCORE)
                .build();
    }

    public void recordSubmitted(String reporterUsername) {
        this.reporterUsername = reporterUsername;
        this.submittedCount++;
    }

    public void recordAccepted(String reporterUsername) {
        this.reporterUsername = reporterUsername;
        this.acceptedCount++;
        recalculateTrustScore();
    }

    public void recordDeclined(String reporterUsername) {
        this.reporterUsername = reporterUsername;
        this.declinedCount++;
        this.falseReportCount++;
        recalculateTrustScore();
    }

    public boolean isRestricted(LocalDateTime now) {
        return restrictedUntil != null && restrictedUntil.isAfter(now);
    }

    public void restrictUntil(LocalDateTime restrictedUntil, String reason) {
        this.restrictedUntil = restrictedUntil;
        this.restrictionReason = reason;
    }

    public void clearExpiredRestriction(LocalDateTime now) {
        if (restrictedUntil != null && !restrictedUntil.isAfter(now)) {
            this.restrictedUntil = null;
            this.restrictionReason = null;
            this.falseReportCount = 0L;
            recalculateTrustScore();
        }
    }

    private void recalculateTrustScore() {
        long calculated = DEFAULT_TRUST_SCORE + acceptedCount * 5L - falseReportCount * 20L;
        this.trustScore = (int) Math.max(MIN_TRUST_SCORE, Math.min(MAX_TRUST_SCORE, calculated));
    }
}
