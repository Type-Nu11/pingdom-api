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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "trust_score_anomaly",
        indexes = {
                @Index(name = "idx_trust_score_anomaly_reporter_detected", columnList = "reporter_user_id, detected_at DESC, id DESC"),
                @Index(name = "idx_trust_score_anomaly_type_severity", columnList = "anomaly_type, severity, detected_at DESC"),
                @Index(name = "idx_trust_score_anomaly_unresolved", columnList = "resolved_at, detected_at DESC")
        }
)
public class TrustScoreAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false, length = 30)
    private TrustScoreAnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private TrustScoreAnomalySeverity severity;

    @Column(name = "baseline_score", nullable = false)
    private int baselineScore;

    @Column(name = "observed_score", nullable = false)
    private int observedScore;

    @Column(name = "submitted_count", nullable = false)
    private long submittedCount;

    @Column(name = "accepted_count", nullable = false)
    private long acceptedCount;

    @Column(name = "declined_count", nullable = false)
    private long declinedCount;

    @Column(name = "false_report_count", nullable = false)
    private long falseReportCount;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
