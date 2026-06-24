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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "place_recommendation_traffic_policy")
public class PlaceRecommendationTrafficPolicy {

    @Id
    @Column(name = "recommendation_version", nullable = false, length = 100)
    private String recommendationVersion;

    @Column(name = "traffic_percentage", nullable = false)
    private int trafficPercentage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PlaceRecommendationTrafficPolicy create(String recommendationVersion, int trafficPercentage) {
        return PlaceRecommendationTrafficPolicy.builder()
                .recommendationVersion(recommendationVersion)
                .trafficPercentage(trafficPercentage)
                .build();
    }

    public void updateTrafficPercentage(int trafficPercentage) {
        this.trafficPercentage = trafficPercentage;
    }
}
