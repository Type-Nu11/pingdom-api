package com.typenull.pingdom.moderation.domain.appeal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "report_appeal")
public class ReportAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "appellant_user_id", nullable = false)
    private Long appellantUserId;

    @Column(name = "appellant_username", nullable = false, length = 50)
    private String appellantUsername;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportAppealStatus status = ReportAppealStatus.SUBMITTED;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "admin_reason", length = 500)
    private String adminReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isSubmitted() {
        return status == ReportAppealStatus.SUBMITTED;
    }

    public void approve(Long adminUserId, String adminReason, LocalDateTime processedAt) {
        this.status = ReportAppealStatus.APPROVED;
        this.adminUserId = adminUserId;
        this.adminReason = adminReason;
        this.processedAt = processedAt;
    }

    public void reject(Long adminUserId, String adminReason, LocalDateTime processedAt) {
        this.status = ReportAppealStatus.REJECTED;
        this.adminUserId = adminUserId;
        this.adminReason = adminReason;
        this.processedAt = processedAt;
    }
}
