package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantVerificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    /**
     * 본인 승인·사업자 거절이면 각각의 상태를 반영하고 완전 승인으로 판정하지 않는지 검증.
     */
    @Test
    void requiresBothVerificationApprovals() {
        MerchantVerification verification = pendingVerification();

        verification.review(99L, true, false, "사업자 정보 불일치", NOW.plusMinutes(1));

        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.APPROVED);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.REJECTED);
        assertThat(verification.isFullyApproved()).isFalse();
    }

    /**
     * 본인과 사업자를 모두 승인하면 완전 승인 상태와 검토자 ID를 기록하는지 검증.
     */
    @Test
    void acceptsBothVerificationApprovals() {
        MerchantVerification verification = pendingVerification();

        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        assertThat(verification.isFullyApproved()).isTrue();
        assertThat(verification.getReviewedBy()).isEqualTo(99L);
    }

    /**
     * 거절 후 재신청은 두 검증을 PENDING으로 돌리고 검토자를 지우며 새 암호화 사업자번호를 보관하는지 검증.
     */
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

    /**
     * 이미 승인된 검증의 일반 수정은 IllegalStateException으로 거절되는지 검증.
     */
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

    /**
     * 탈퇴 익명화가 이름·상호·암호화 번호·검토 사유를 대체/제거하되 과거 검토 시각은 보존하는지 검증.
     */
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

    /**
     * 상호 변경은 새 상호와 두 PENDING 상태를 반영하고 검토자·시각·사유를 초기화하는지 검증.
     */
    @Test
    void businessNameChangeInvalidatesVerification() {
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

    /**
     * 승인 후에도 새 대표자·암호화 번호로 증빙을 재제출하면 두 검증이 PENDING으로 돌아가는지 검증.
     */
    @Test
    void resubmitsApprovedVerificationEvidence() {
        MerchantVerification verification = pendingVerification();
        verification.review(99L, true, true, "확인 완료", NOW.plusMinutes(1));

        verification.resubmit("새 대표자", "핑덤 카페", "encrypted-987", NOW.plusMinutes(2));

        assertThat(verification.getLegalName()).isEqualTo("새 대표자");
        assertThat(verification.getEncryptedBusinessRegistrationNumber()).isEqualTo("encrypted-987");
        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
    }

    /**
     * 고정 시각과 암호화 사업자번호를 가진 사용자 1의 본인·사업자 검증 대기 상태를 생성.
     */
    private MerchantVerification pendingVerification() {
        return MerchantVerification.pending(1L, "김핑덤", "핑덤 카페", "encrypted-123", NOW);
    }
}
