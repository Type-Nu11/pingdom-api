package com.typenull.pingdom.place.domain.registration;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통합 신청의 심사 결과와 승인 직전의 소유권 영향 범위를 보존합니다.
 * JSON 스냅샷은 이후 운영 데이터가 변경되어도 당시 심사 근거를 재현하기 위한 불변 감사 데이터입니다.
 */
@Getter
@Entity
@Table(name = "merchant_place_application_review_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantPlaceApplicationReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", nullable = false, length = 20)
    private PlaceRegistrationStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 20)
    private PlaceRegistrationStatus afterStatus;

    @Column(name = "reviewed_version", nullable = false)
    private long reviewedVersion;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "previous_owner_snapshot", nullable = false, columnDefinition = "TEXT")
    private String previousOwnerSnapshot;

    @Column(name = "team_snapshot", nullable = false, columnDefinition = "TEXT")
    private String teamSnapshot;

    @Column(name = "offer_snapshot", nullable = false, columnDefinition = "TEXT")
    private String offerSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private MerchantPlaceApplicationReviewHistory(
            Long applicationId,
            Long adminUserId,
            PlaceRegistrationStatus beforeStatus,
            PlaceRegistrationStatus afterStatus,
            long reviewedVersion,
            String reason,
            String previousOwnerSnapshot,
            String teamSnapshot,
            String offerSnapshot,
            LocalDateTime createdAt
    ) {
        this.applicationId = applicationId;
        this.adminUserId = adminUserId;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.reviewedVersion = reviewedVersion;
        this.reason = reason;
        this.previousOwnerSnapshot = previousOwnerSnapshot;
        this.teamSnapshot = teamSnapshot;
        this.offerSnapshot = offerSnapshot;
        this.createdAt = createdAt;
    }

    public static MerchantPlaceApplicationReviewHistory create(
            Long applicationId,
            Long adminUserId,
            PlaceRegistrationStatus beforeStatus,
            PlaceRegistrationStatus afterStatus,
            long reviewedVersion,
            String reason,
            String previousOwnerSnapshot,
            String teamSnapshot,
            String offerSnapshot,
            LocalDateTime createdAt
    ) {
        return new MerchantPlaceApplicationReviewHistory(
                applicationId,
                adminUserId,
                beforeStatus,
                afterStatus,
                reviewedVersion,
                reason,
                previousOwnerSnapshot,
                teamSnapshot,
                offerSnapshot,
                createdAt
        );
    }
}
