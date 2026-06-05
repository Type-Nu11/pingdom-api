package com.typenull.pingdom.place.domain;

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

@Entity
@Table(name = "place_recommendation_snapshot")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationSnapshot {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "photo_count", nullable = false)
    private long photoCount;

    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    @Column(name = "total_like_count", nullable = false)
    private long totalLikeCount;

    @Column(name = "latest_post_created_at")
    private LocalDateTime latestPostCreatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void synchronize(
            long photoCount,
            long bookmarkCount,
            long totalLikeCount,
            LocalDateTime latestPostCreatedAt,
            LocalDateTime updatedAt
    ) {
        this.photoCount = photoCount;
        this.bookmarkCount = bookmarkCount;
        this.totalLikeCount = totalLikeCount;
        this.latestPostCreatedAt = latestPostCreatedAt;
        this.updatedAt = updatedAt;
    }
}
