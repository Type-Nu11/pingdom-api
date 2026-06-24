package com.typenull.pingdom.place.domain.recommendation;

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
@Table(name = "place_recommendation_exposure")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceRecommendationExposure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_recommendation_exposure_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "request_latitude", nullable = false)
    private Double requestLatitude;

    @Column(name = "request_longitude", nullable = false)
    private Double requestLongitude;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

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
