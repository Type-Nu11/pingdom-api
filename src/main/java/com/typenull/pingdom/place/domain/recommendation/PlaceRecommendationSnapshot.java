package com.typenull.pingdom.place.domain.recommendation;

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

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "bookmark_conversion_count", nullable = false)
    private long bookmarkConversionCount;

    @Column(name = "like_conversion_count", nullable = false)
    private long likeConversionCount;

    @Column(name = "exposure_count", nullable = false)
    private long exposureCount;

    @Column(name = "latest_post_created_at")
    private LocalDateTime latestPostCreatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void synchronize(
            long photoCount,
            long bookmarkCount,
            long totalLikeCount,
            long clickCount,
            long bookmarkConversionCount,
            long likeConversionCount,
            long exposureCount,
            LocalDateTime latestPostCreatedAt,
            LocalDateTime updatedAt
    ) {
        this.photoCount = photoCount;
        this.bookmarkCount = bookmarkCount;
        this.totalLikeCount = totalLikeCount;
        this.clickCount = clickCount;
        this.bookmarkConversionCount = bookmarkConversionCount;
        this.likeConversionCount = likeConversionCount;
        this.exposureCount = exposureCount;
        this.latestPostCreatedAt = latestPostCreatedAt;
        this.updatedAt = updatedAt;
    }

    public void increaseClickCount(long delta, LocalDateTime updatedAt) {
        this.clickCount += delta;
        this.updatedAt = updatedAt;
    }

    public void increaseExposureCount(long delta, LocalDateTime updatedAt) {
        this.exposureCount += delta;
        this.updatedAt = updatedAt;
    }

    public void increaseBookmarkConversionCount(long delta, LocalDateTime updatedAt) {
        this.bookmarkConversionCount += delta;
        this.updatedAt = updatedAt;
    }

    public void increaseLikeConversionCount(long delta, LocalDateTime updatedAt) {
        this.likeConversionCount += delta;
        this.updatedAt = updatedAt;
    }
}
