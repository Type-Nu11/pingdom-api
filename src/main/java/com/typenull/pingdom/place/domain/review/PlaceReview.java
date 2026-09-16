package com.typenull.pingdom.place.domain.review;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "place_review")
public class PlaceReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_id", nullable = false) private MapPlace place;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "recommend_reason", nullable = false, length = 100) private String recommendReason;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "place_review_recommend_reason", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @OrderColumn(name = "display_order")
    @Column(name = "reason", nullable = false, length = 50)
    private List<PlaceReviewRecommendReason> recommendReasons = new ArrayList<>();
    @Column(nullable = false, length = 2000) private String content;
    @ElementCollection @CollectionTable(name = "place_review_image", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url", nullable = false, length = 500) private List<String> imageUrls;
    @OneToMany(mappedBy = "review", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<PlaceReviewMediaUpload> mediaUploads = new ArrayList<>();
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status", nullable = false, length = 20)
    private PlaceReviewVisibilityStatus visibilityStatus;
    @Column(name = "hidden_at") private LocalDateTime hiddenAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    private PlaceReview(
            MapPlace place,
            Long userId,
            String reason,
            List<PlaceReviewRecommendReason> recommendReasons,
            String content,
            List<String> imageUrls,
            LocalDateTime now
    ) {
        this.place=place; this.userId=userId; this.recommendReason=reason.trim(); this.recommendReasons=new ArrayList<>(recommendReasons);
        this.content=content.trim(); this.imageUrls=new ArrayList<>(imageUrls); this.createdAt=now;
        this.visibilityStatus = PlaceReviewVisibilityStatus.VISIBLE;
    }
    public static PlaceReview create(
            MapPlace place,
            Long userId,
            String reason,
            List<PlaceReviewRecommendReason> recommendReasons,
            String content,
            List<String> imageUrls,
            LocalDateTime now
    ) {
        return new PlaceReview(place,userId,reason,recommendReasons,content,imageUrls,now);
    }

    public void addMediaUpload(PlaceReviewMediaUpload mediaUpload) {
        mediaUploads.add(mediaUpload);
        imageUrls.add(mediaUpload.getImageUrl());
    }

    public void hide(LocalDateTime now) {
        if (visibilityStatus == PlaceReviewVisibilityStatus.DELETED) {
            throw new IllegalStateException("삭제된 리뷰는 숨김 처리할 수 없습니다.");
        }
        if (visibilityStatus == PlaceReviewVisibilityStatus.VISIBLE) {
            visibilityStatus = PlaceReviewVisibilityStatus.HIDDEN;
            hiddenAt = now;
        }
    }

    public void markDeleted(LocalDateTime now) {
        if (visibilityStatus == PlaceReviewVisibilityStatus.DELETED) {
            throw new IllegalStateException("이미 삭제된 리뷰입니다.");
        }
        visibilityStatus = PlaceReviewVisibilityStatus.DELETED;
        deletedAt = now;
    }
}
