package com.typenull.pingdom.engagement.domain;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.typenull.pingdom.post.domain.MapImage;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "post_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_post_report_user_image",
                        columnNames = {"reporter_user_id", "map_image_id"}
                )
        }
)
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    @Column(name = "reported_image_id", nullable = false)
    private Long reportedImageId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    @Column(name = "reported_image_url", nullable = false, length = 500)
    private String reportedImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_image_id")
    private MapImage mapImage;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostReportStatus status = PostReportStatus.PENDING;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public boolean isPending() {
        return status == PostReportStatus.PENDING;
    }

    public void accept(LocalDateTime processedAt) {
        this.status = PostReportStatus.ACCEPTED;
        this.processedAt = processedAt;
    }

    public void decline(LocalDateTime processedAt) {
        this.status = PostReportStatus.DECLINED;
        this.processedAt = processedAt;
    }

    public void restore(LocalDateTime processedAt) {
        this.status = PostReportStatus.RESTORED;
        this.processedAt = processedAt;
    }

    public void detachMapImage() {
        this.mapImage = null;
    }
}
