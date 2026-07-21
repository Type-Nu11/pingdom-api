package com.typenull.pingdom.place.domain.recommendation.engagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "place_recommendation_conversion",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_recommendation_conversion_user_place_type",
                        columnNames = {"user_id", "place_id", "conversion_type"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_recommendation_conversion_id")
    private Long id;

    @Column(name = "place_recommendation_click_id", nullable = false)
    private Long placeRecommendationClickId;

    @Column(name = "place_recommendation_feature_log_id")
    private Long placeRecommendationFeatureLogId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_type", nullable = false, length = 20)
    private PlaceRecommendationConversionType conversionType;

    @Column(name = "recommendation_version", nullable = false, length = 50)
    private String recommendationVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignPlace(Long targetPlaceId) {
        this.placeId = targetPlaceId;
    }
}
