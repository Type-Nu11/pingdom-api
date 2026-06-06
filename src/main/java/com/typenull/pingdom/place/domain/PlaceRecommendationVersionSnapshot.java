package com.typenull.pingdom.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "place_recommendation_version_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_recommendation_version_snapshot_place_version",
                        columnNames = {"place_id", "recommendation_version"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationVersionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_recommendation_version_snapshot_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "recommendation_version", nullable = false, length = 50)
    private String recommendationVersion;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "bookmark_conversion_count", nullable = false)
    private long bookmarkConversionCount;

    @Column(name = "like_conversion_count", nullable = false)
    private long likeConversionCount;

    @Column(name = "exposure_count", nullable = false)
    private long exposureCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void synchronize(
            long clickCount,
            long bookmarkConversionCount,
            long likeConversionCount,
            long exposureCount,
            LocalDateTime updatedAt
    ) {
        this.clickCount = clickCount;
        this.bookmarkConversionCount = bookmarkConversionCount;
        this.likeConversionCount = likeConversionCount;
        this.exposureCount = exposureCount;
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
