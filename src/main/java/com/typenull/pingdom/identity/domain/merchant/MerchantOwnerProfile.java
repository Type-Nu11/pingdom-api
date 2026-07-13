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
@Table(name = "merchant_owner_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantOwnerProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantOwnerStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MerchantOwnerProfile pending(
            Long userId,
            String businessName,
            String displayName,
            String description,
            String contactEmail,
            String contactPhone,
            LocalDateTime now
    ) {
        return MerchantOwnerProfile.builder()
                .userId(userId)
                .businessName(businessName)
                .displayName(displayName)
                .description(description)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .status(MerchantOwnerStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void update(
            String businessName,
            String displayName,
            String description,
            String contactEmail,
            String contactPhone,
            LocalDateTime now
    ) {
        if (status != MerchantOwnerStatus.PENDING && status != MerchantOwnerStatus.ACTIVE) {
            throw new IllegalStateException("현재 상태에서는 Merchant Owner 프로필을 수정할 수 없습니다.");
        }
        applyProfile(businessName, displayName, description, contactEmail, contactPhone, now);
    }

    public void reapply(
            String businessName,
            String displayName,
            String description,
            String contactEmail,
            String contactPhone,
            LocalDateTime now
    ) {
        if (status != MerchantOwnerStatus.REJECTED && status != MerchantOwnerStatus.REVOKED) {
            throw new IllegalStateException("거절되거나 회수된 프로필만 재신청할 수 있습니다.");
        }
        applyProfile(businessName, displayName, description, contactEmail, contactPhone, now);
        status = MerchantOwnerStatus.PENDING;
        reviewedBy = null;
        reviewedAt = null;
    }

    public void approve(Long adminUserId, LocalDateTime now) {
        if (status == MerchantOwnerStatus.ACTIVE) {
            return;
        }
        requirePending();
        status = MerchantOwnerStatus.ACTIVE;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void reject(Long adminUserId, LocalDateTime now) {
        if (status == MerchantOwnerStatus.REJECTED) {
            return;
        }
        requirePending();
        status = MerchantOwnerStatus.REJECTED;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void revoke(Long adminUserId, LocalDateTime now) {
        if (status == MerchantOwnerStatus.REVOKED) {
            return;
        }
        if (status != MerchantOwnerStatus.ACTIVE) {
            throw new IllegalStateException("활성 Merchant Owner 권한만 회수할 수 있습니다.");
        }
        status = MerchantOwnerStatus.REVOKED;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void anonymize(LocalDateTime now) {
        status = MerchantOwnerStatus.REVOKED;
        businessName = "탈퇴 Merchant Owner";
        displayName = "탈퇴 Merchant Owner";
        description = null;
        contactEmail = "withdrawn@withdrawn.local";
        contactPhone = "WITHDRAWN";
        reviewedBy = null;
        reviewedAt = now;
        updatedAt = now;
    }

    private void applyProfile(
            String businessName,
            String displayName,
            String description,
            String contactEmail,
            String contactPhone,
            LocalDateTime now
    ) {
        this.businessName = businessName;
        this.displayName = displayName;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (status != MerchantOwnerStatus.PENDING) {
            throw new IllegalStateException("대기 중인 Merchant Owner 신청만 심사할 수 있습니다.");
        }
    }
}
