package com.typenull.pingdom.community.domain;

import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_report", uniqueConstraints = {
        @UniqueConstraint(name = "uk_community_report_reporter_post", columnNames = {"reporter_user_id", "community_post_id"}),
        @UniqueConstraint(name = "uk_community_report_reporter_comment", columnNames = {"reporter_user_id", "community_post_comment_id"})
})
public class CommunityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_report_id")
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_id")
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_post_comment_id")
    private CommunityPostComment comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private CommunityReportReason reason;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunityReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_by_admin_user_id")
    private Long processedByAdminUserId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private CommunityReport(Long reporterUserId, CommunityPost post, CommunityPostComment comment,
                            CommunityReportReason reason, String description, LocalDateTime createdAt) {
        if (reporterUserId == null || reporterUserId <= 0 || (post == null) == (comment == null)
                || reason == null || description == null || description.isBlank()
                || description.length() > 500 || createdAt == null) {
            throw new CommunityException(CommunityErrorCode.INVALID_REPORT);
        }
        this.reporterUserId = reporterUserId;
        this.post = post;
        this.comment = comment;
        this.reason = reason;
        this.description = description.strip();
        this.createdAt = createdAt;
        this.status = CommunityReportStatus.PENDING;
    }

    // 게시글과 댓글 중 정확히 하나만 신고 대상으로 지정한다.
    public static CommunityReport reportPost(Long reporterUserId, CommunityPost post,
                                            CommunityReportReason reason, String description, LocalDateTime createdAt) {
        return new CommunityReport(reporterUserId, post, null, reason, description, createdAt);
    }

    public static CommunityReport reportComment(Long reporterUserId, CommunityPostComment comment,
                                               CommunityReportReason reason, String description, LocalDateTime createdAt) {
        return new CommunityReport(reporterUserId, null, comment, reason, description, createdAt);
    }

    public CommunityReportTargetType getTargetType() {
        return post == null ? CommunityReportTargetType.COMMENT : CommunityReportTargetType.POST;
    }

    public Long getTargetId() {
        return post == null ? comment.getId() : post.getId();
    }

    public void accept(Long adminUserId, LocalDateTime processedAt) {
        process(CommunityReportStatus.ACCEPTED, adminUserId, processedAt);
    }

    public void decline(Long adminUserId, LocalDateTime processedAt) {
        process(CommunityReportStatus.DECLINED, adminUserId, processedAt);
    }

    // 처리된 신고는 되돌리지 않으며 동시 수정은 version으로 검증한다.
    private void process(CommunityReportStatus nextStatus, Long adminUserId, LocalDateTime processedAt) {
        if (status != CommunityReportStatus.PENDING) {
            throw new CommunityException(CommunityErrorCode.REPORT_ALREADY_PROCESSED);
        }
        if (adminUserId == null || adminUserId <= 0 || processedAt == null || processedAt.isBefore(createdAt)) {
            throw new CommunityException(CommunityErrorCode.INVALID_REPORT);
        }
        this.status = nextStatus;
        this.processedByAdminUserId = adminUserId;
        this.processedAt = processedAt;
    }
}
