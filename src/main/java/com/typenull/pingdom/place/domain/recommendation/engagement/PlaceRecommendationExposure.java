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

/**
 * 추천 요청 좌표와 결과의 1부터 시작하는 순위를 기록한 노출 행입니다.
 * 익명 요청은 userId가 null이며 집계는 행 수를 사용하므로 사용자별 고유 노출 수와 다릅니다.
 */
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
