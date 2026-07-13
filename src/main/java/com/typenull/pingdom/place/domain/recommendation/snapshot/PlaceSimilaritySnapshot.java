package com.typenull.pingdom.place.domain.recommendation.snapshot;

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
        name = "place_similarity_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_similarity_snapshot_pair",
                        columnNames = {"left_place_id", "right_place_id"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceSimilaritySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_similarity_snapshot_id")
    private Long id;

    @Column(name = "left_place_id", nullable = false)
    private Long leftPlaceId;

    @Column(name = "right_place_id", nullable = false)
    private Long rightPlaceId;

    @Column(name = "geo_kernel_score", nullable = false)
    private double geoKernelScore;

    @Column(name = "co_bookmark_pmi_score", nullable = false)
    private double coBookmarkPmiScore;

    @Column(name = "co_like_cosine_score", nullable = false)
    private double coLikeCosineScore;

    @Column(name = "trend_similarity_score", nullable = false)
    private double trendSimilarityScore;

    @Column(name = "total_similarity_score", nullable = false)
    private double totalSimilarityScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void synchronize(
            double geoKernelScore,
            double coBookmarkPmiScore,
            double coLikeCosineScore,
            double trendSimilarityScore,
            double totalSimilarityScore,
            LocalDateTime updatedAt
    ) {
        this.geoKernelScore = geoKernelScore;
        this.coBookmarkPmiScore = coBookmarkPmiScore;
        this.coLikeCosineScore = coLikeCosineScore;
        this.trendSimilarityScore = trendSimilarityScore;
        this.totalSimilarityScore = totalSimilarityScore;
        this.updatedAt = updatedAt;
    }
}
