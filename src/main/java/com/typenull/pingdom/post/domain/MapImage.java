package com.typenull.pingdom.post.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.typenull.pingdom.place.domain.place.MapPlace;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Table(
//        uniqueConstraints = {
//                @UniqueConstraint(
//                        name = "uk_map_image_user_place",
//                        columnNames = {"user_id", "map_place_id"}
//                )
//        }
//)
public class MapImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_image_id")
    private Long id;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Column(name = "s3_key", length = 500, nullable = false)
    private String s3Key;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "created_time")
    @CreationTimestamp // save시 자동으로 현재시간 저장
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "like_count")
    private long likeCount = 0L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status", nullable = false, length = 30)
    private MapImageVisibilityStatus visibilityStatus = MapImageVisibilityStatus.ACTIVE;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    @Column(name = "restored_reason", length = 500)
    private String restoredReason;

    @Column(name = "visibility_updated_by")
    private Long visibilityUpdatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_place_id")
    private MapPlace mapPlace;

    public void update(String title, String description, String imageUrl, String s3Key) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.s3Key = s3Key;
    }

    public void reassignPlace(MapPlace targetPlace) {
        this.mapPlace = targetPlace;
    }

    public boolean isVisible() {
        return visibilityStatus == MapImageVisibilityStatus.ACTIVE;
    }

    public void autoHide(String reason, LocalDateTime hiddenAt, Long updatedBy) {
        if (!isVisible()) {
            return;
        }
        this.visibilityStatus = MapImageVisibilityStatus.AUTO_HIDDEN;
        this.hiddenAt = hiddenAt;
        this.hiddenReason = reason;
        this.restoredAt = null;
        this.restoredReason = null;
        this.visibilityUpdatedBy = updatedBy;
    }

    public void restore(String reason, LocalDateTime restoredAt, Long updatedBy) {
        if (isVisible()) {
            return;
        }
        this.visibilityStatus = MapImageVisibilityStatus.ACTIVE;
        this.restoredAt = restoredAt;
        this.restoredReason = reason;
        this.visibilityUpdatedBy = updatedBy;
    }
}
