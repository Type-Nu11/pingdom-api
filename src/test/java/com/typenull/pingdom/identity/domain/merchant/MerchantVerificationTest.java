package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantVerificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void identityAndBusinessMustBothBeApproved() {
        MerchantVerification verification = pendingVerification();

        verification.review(99L, true, false, "사업자 정보 불일치", NOW.plusMinutes(1));

        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.APPROVED);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.REJECTED);
        assertThat(verification.isFullyApproved()).isFalse();
    }

    @Test
    void approvalOfBothVerificationsMakesSubmissionEligible() {
        MerchantVerification verification = pendingVerification();

        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        assertThat(verification.isFullyApproved()).isTrue();
        assertThat(verification.getReviewedBy()).isEqualTo(99L);
    }

    @Test
    void rejectedVerificationCanBeReapplied() {
        MerchantVerification verification = pendingVerification();
        verification.review(99L, false, true, "신원 불일치", NOW.plusMinutes(1));

        verification.reapply("김핑덤", "핑덤 카페", "encrypted-987", NOW.plusMinutes(2));

        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getReviewedBy()).isNull();
        assertThat(verification.getEncryptedBusinessRegistrationNumber()).isEqualTo("encrypted-987");
    }

    @Test
    void reviewedVerificationCannotBeUpdated() {
        MerchantVerification verification = pendingVerification();
        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        assertThatThrownBy(() -> verification.update(
                "수정 이름",
                "핑덤 카페",
                "encrypted-987",
                NOW.plusMinutes(2)
        ))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withdrawalAnonymizesStoredIdentityData() {
        MerchantVerification verification = pendingVerification();
        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        verification.anonymize("encrypted-anonymized", NOW.plusMinutes(2));

        assertThat(verification.getLegalName()).isEqualTo("탈퇴 사용자");
        assertThat(verification.getBusinessName()).isEqualTo("탈퇴 사업자");
        assertThat(verification.getEncryptedBusinessRegistrationNumber()).isEqualTo("encrypted-anonymized");
        assertThat(verification.getReviewReason()).isNull();
        assertThat(verification.getReviewedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void businessNameChangeInvalidatesApprovedVerification() {
        MerchantVerification verification = pendingVerification();
        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        verification.invalidateForBusinessProfileChange("새 상호", NOW.plusMinutes(2));

        assertThat(verification.getBusinessName()).isEqualTo("새 상호");
        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getReviewedBy()).isNull();
        assertThat(verification.getReviewedAt()).isNull();
        assertThat(verification.getReviewReason()).isNull();
    }

    private MerchantVerification pendingVerification() {
        return MerchantVerification.pending(1L, "김핑덤", "핑덤 카페", "encrypted-123", NOW);
    }
}
