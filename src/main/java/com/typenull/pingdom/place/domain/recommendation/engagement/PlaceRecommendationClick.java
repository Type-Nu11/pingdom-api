package com.typenull.pingdom.place.domain.recommendation.engagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "place_recommendation_click")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_recommendation_click_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "recommendation_version", nullable = false, length = 50)
    private String recommendationVersion;

    @Column(name = "request_id", length = 50)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignPlace(Long placeId) {
        this.placeId = placeId;
    }
}
