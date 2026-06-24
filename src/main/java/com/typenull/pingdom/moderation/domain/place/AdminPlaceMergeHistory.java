package com.typenull.pingdom.moderation.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(
        name = "admin_place_merge_history",
        indexes = {
                @Index(name = "idx_admin_place_merge_history_created", columnList = "merged_at DESC, id DESC"),
                @Index(name = "idx_admin_place_merge_history_source", columnList = "source_place_id"),
                @Index(name = "idx_admin_place_merge_history_target", columnList = "target_place_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminPlaceMergeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_place_id", nullable = false)
    private Long sourcePlaceId;

    @Column(name = "target_place_id", nullable = false)
    private Long targetPlaceId;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "source_place_snapshot", nullable = false, columnDefinition = "TEXT")
    private String sourcePlaceSnapshot;

    @Column(name = "target_place_snapshot", nullable = false, columnDefinition = "TEXT")
    private String targetPlaceSnapshot;

    @Column(name = "moved_image_ids", nullable = false, columnDefinition = "TEXT")
    private String movedImageIds;

    @Column(name = "moved_bookmark_ids", nullable = false, columnDefinition = "TEXT")
    private String movedBookmarkIds;

    @Column(name = "deleted_bookmarks", nullable = false, columnDefinition = "TEXT")
    private String deletedBookmarks;

    @Column(name = "moved_conversion_ids", nullable = false, columnDefinition = "TEXT")
    private String movedConversionIds;

    @Column(name = "deleted_conversions", nullable = false, columnDefinition = "TEXT")
    private String deletedConversions;

    @Column(name = "moved_click_ids", nullable = false, columnDefinition = "TEXT")
    private String movedClickIds;

    @Column(name = "moved_exposure_ids", nullable = false, columnDefinition = "TEXT")
    private String movedExposureIds;

    @Column(name = "moved_feature_log_ids", nullable = false, columnDefinition = "TEXT")
    private String movedFeatureLogIds;

    @Column(name = "restored", nullable = false)
    @Builder.Default
    private boolean restored = false;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    @Column(name = "merged_at", nullable = false)
    private LocalDateTime mergedAt;

    public void markRestored(LocalDateTime restoredAt) {
        this.restored = true;
        this.restoredAt = restoredAt;
    }
}
