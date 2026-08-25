package com.typenull.pingdom.place.domain.review;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "place_review")
public class PlaceReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_id", nullable = false) private MapPlace place;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "recommend_reason", nullable = false, length = 100) private String recommendReason;
    @Column(nullable = false, length = 2000) private String content;
    @ElementCollection @CollectionTable(name = "place_review_image", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url", nullable = false, length = 500) private List<String> imageUrls;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status", nullable = false, length = 20)
    private PlaceReviewVisibilityStatus visibilityStatus;
    @Column(name = "hidden_at") private LocalDateTime hiddenAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    private PlaceReview(MapPlace place, Long userId, String reason, String content, List<String> imageUrls, LocalDateTime now) {
        this.place=place; this.userId=userId; this.recommendReason=reason.trim(); this.content=content.trim(); this.imageUrls=List.copyOf(imageUrls); this.createdAt=now;
        this.visibilityStatus = PlaceReviewVisibilityStatus.VISIBLE;
    }
    public static PlaceReview create(MapPlace place, Long userId, String reason, String content, List<String> imageUrls, LocalDateTime now) {
        return new PlaceReview(place,userId,reason,content,imageUrls,now);
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
