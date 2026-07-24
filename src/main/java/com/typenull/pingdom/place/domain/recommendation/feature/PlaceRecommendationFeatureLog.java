package com.typenull.pingdom.place.domain.recommendation.feature;

import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "place_recommendation_feature_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationFeatureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_recommendation_feature_log_id")
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "recommendation_version", nullable = false, length = 50)
    private String recommendationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_stage", nullable = false, length = 20)
    private RecommendationStage recommendationStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_source", nullable = false, length = 20)
    private PlaceRecommendationCandidateSource candidateSource;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "geo_score", nullable = false)
    private double geoScore;

    @Column(name = "personal_score", nullable = false)
    private double personalScore;

    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    @Column(name = "engagement_score", nullable = false)
    private double engagementScore;

    @Column(name = "conversion_score", nullable = false)
    private double conversionScore;

    @Column(name = "exploration_score", nullable = false)
    private double explorationScore;

    @Column(name = "freshness_score", nullable = false)
    private double freshnessScore;

    @Column(name = "trust_score", nullable = false)
    private double trustScore;

    @Column(name = "context_score", nullable = false)
    private double contextScore;

    @Column(name = "benefit_score", nullable = false)
    private double benefitScore;

    @Column(name = "availability_score", nullable = false)
    private double availabilityScore;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignPlace(Long placeId) {
        this.placeId = placeId;
    }
}
