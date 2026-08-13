package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "merchant_place_claim_review_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantPlaceClaimReviewHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "claim_id", nullable = false) private Long claimId;
    @Column(name = "admin_user_id", nullable = false) private Long adminUserId;
    @Enumerated(EnumType.STRING) @Column(name = "before_status", nullable = false) private MerchantPlaceClaimStatus beforeStatus;
    @Enumerated(EnumType.STRING) @Column(name = "after_status", nullable = false) private MerchantPlaceClaimStatus afterStatus;
    @Column(name = "reviewed_version", nullable = false) private long reviewedVersion;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    private MerchantPlaceClaimReviewHistory(Long claimId, Long adminUserId, MerchantPlaceClaimStatus beforeStatus,
            MerchantPlaceClaimStatus afterStatus, long reviewedVersion, String reason, LocalDateTime createdAt) {
        this.claimId = claimId; this.adminUserId = adminUserId; this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus; this.reviewedVersion = reviewedVersion; this.reason = reason; this.createdAt = createdAt;
    }

    public static MerchantPlaceClaimReviewHistory create(Long claimId, Long adminUserId,
            MerchantPlaceClaimStatus beforeStatus, MerchantPlaceClaimStatus afterStatus,
            long reviewedVersion, String reason, LocalDateTime createdAt) {
        return new MerchantPlaceClaimReviewHistory(claimId, adminUserId, beforeStatus, afterStatus, reviewedVersion, reason, createdAt);
    }
}
