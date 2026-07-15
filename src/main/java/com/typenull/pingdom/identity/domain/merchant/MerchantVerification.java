package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "merchant_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantVerification {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "legal_name", nullable = false, length = 100)
    private String legalName;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "business_registration_number", nullable = false, length = 255)
    private String encryptedBusinessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false, length = 20)
    private MerchantVerificationStatus identityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_status", nullable = false, length = 20)
    private MerchantVerificationStatus businessStatus;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MerchantVerification pending(
            Long userId,
            String legalName,
            String businessName,
            String encryptedBusinessRegistrationNumber,
            LocalDateTime now
    ) {
        return MerchantVerification.builder()
                .userId(userId)
                .legalName(legalName)
                .businessName(businessName)
                .encryptedBusinessRegistrationNumber(encryptedBusinessRegistrationNumber)
                .identityStatus(MerchantVerificationStatus.PENDING)
                .businessStatus(MerchantVerificationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void update(
            String legalName,
            String businessName,
            String encryptedBusinessRegistrationNumber,
            LocalDateTime now
    ) {
        requirePending();
        applySubmission(legalName, businessName, encryptedBusinessRegistrationNumber, now);
    }

    public void reapply(
            String legalName,
            String businessName,
            String encryptedBusinessRegistrationNumber,
            LocalDateTime now
    ) {
        if (identityStatus != MerchantVerificationStatus.REJECTED
                && businessStatus != MerchantVerificationStatus.REJECTED) {
            throw new IllegalStateException("거절된 Merchant 검증만 재신청할 수 있습니다.");
        }
        applySubmission(legalName, businessName, encryptedBusinessRegistrationNumber, now);
        identityStatus = MerchantVerificationStatus.PENDING;
        businessStatus = MerchantVerificationStatus.PENDING;
        reviewReason = null;
        reviewedBy = null;
        reviewedAt = null;
    }

    public void review(
            Long adminUserId,
            boolean identityApproved,
            boolean businessApproved,
            String reason,
            LocalDateTime now
    ) {
        MerchantVerificationStatus nextIdentityStatus = result(identityApproved);
        MerchantVerificationStatus nextBusinessStatus = result(businessApproved);
        if (!isPending()) {
            if (identityStatus == nextIdentityStatus && businessStatus == nextBusinessStatus) {
                return;
            }
            throw new IllegalStateException("심사 대기 중인 Merchant 검증만 처리할 수 있습니다.");
        }
        identityStatus = nextIdentityStatus;
        businessStatus = nextBusinessStatus;
        reviewReason = reason;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public boolean isFullyApproved() {
        return identityStatus == MerchantVerificationStatus.APPROVED
                && businessStatus == MerchantVerificationStatus.APPROVED;
    }

    public boolean matchesBusinessName(String currentBusinessName) {
        return businessName.equals(currentBusinessName);
    }

    public void invalidateForBusinessProfileChange(String currentBusinessName, LocalDateTime now) {
        businessName = currentBusinessName;
        identityStatus = MerchantVerificationStatus.PENDING;
        businessStatus = MerchantVerificationStatus.PENDING;
        reviewReason = null;
        reviewedBy = null;
        reviewedAt = null;
        updatedAt = now;
    }

    public void anonymize(String encryptedAnonymizedRegistrationNumber, LocalDateTime now) {
        legalName = "탈퇴 사용자";
        businessName = "탈퇴 사업자";
        encryptedBusinessRegistrationNumber = encryptedAnonymizedRegistrationNumber;
        reviewReason = null;
        reviewedBy = null;
        reviewedAt = now;
        updatedAt = now;
    }

    private void applySubmission(
            String legalName,
            String businessName,
            String encryptedBusinessRegistrationNumber,
            LocalDateTime now
    ) {
        this.legalName = legalName;
        this.businessName = businessName;
        this.encryptedBusinessRegistrationNumber = encryptedBusinessRegistrationNumber;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("심사 대기 중인 Merchant 검증만 수정할 수 있습니다.");
        }
    }

    private boolean isPending() {
        return identityStatus == MerchantVerificationStatus.PENDING
                && businessStatus == MerchantVerificationStatus.PENDING;
    }

    private MerchantVerificationStatus result(boolean approved) {
        return approved ? MerchantVerificationStatus.APPROVED : MerchantVerificationStatus.REJECTED;
    }
}
