package com.typenull.pingdom.engagement.domain.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(
        name = "trust_score_intervention_rule",
        indexes = {
                @Index(name = "idx_trust_score_intervention_rule_enabled", columnList = "enabled, priority, id"),
                @Index(name = "idx_trust_score_intervention_rule_trigger", columnList = "trigger_type, enabled, priority")
        }
)
public class TrustScoreInterventionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private TrustScoreInterventionTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private TrustScoreInterventionAction actionType;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "min_trust_score", nullable = false)
    private int minTrustScore;

    @Column(name = "max_trust_score", nullable = false)
    private int maxTrustScore;

    @Builder.Default
    @Column(name = "min_submitted_count", nullable = false)
    private long minSubmittedCount = 0L;

    @Builder.Default
    @Column(name = "min_false_report_count", nullable = false)
    private long minFalseReportCount = 0L;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean matches(ReporterModerationPolicy policy) {
        if (!enabled || policy == null) {
            return false;
        }
        return policy.getTrustScore() >= minTrustScore
                && policy.getTrustScore() <= maxTrustScore
                && policy.getSubmittedCount() >= minSubmittedCount
                && policy.getFalseReportCount() >= minFalseReportCount;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    public void update(
            String ruleName,
            TrustScoreInterventionTrigger triggerType,
            TrustScoreInterventionAction actionType,
            int minTrustScore,
            int maxTrustScore,
            long minSubmittedCount,
            long minFalseReportCount,
            Integer durationDays,
            int priority,
            String reason
    ) {
        this.ruleName = ruleName;
        this.triggerType = triggerType;
        this.actionType = actionType;
        this.minTrustScore = minTrustScore;
        this.maxTrustScore = maxTrustScore;
        this.minSubmittedCount = minSubmittedCount;
        this.minFalseReportCount = minFalseReportCount;
        this.durationDays = durationDays;
        this.priority = priority;
        this.reason = reason;
    }
}
