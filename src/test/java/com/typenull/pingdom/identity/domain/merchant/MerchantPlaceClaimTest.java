package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantPlaceClaimTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void pendingClaimCanBeApprovedOnce() {
        MerchantPlaceClaim claim = MerchantPlaceClaim.pending(1L, 10L, "사업장 운영자입니다.", NOW);

        claim.approve(99L, "사업자 정보 확인", NOW.plusMinutes(5));

        assertThat(claim.getStatus()).isEqualTo(MerchantPlaceClaimStatus.APPROVED);
        assertThat(claim.getReviewedBy()).isEqualTo(99L);
        assertThat(claim.getReviewReason()).isEqualTo("사업자 정보 확인");
        assertThatThrownBy(() -> claim.reject(99L, "재심사", NOW.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingClaimCanBeCanceledButNotReviewed() {
        MerchantPlaceClaim claim = MerchantPlaceClaim.pending(1L, 10L, "사업장 운영자입니다.", NOW);

        claim.cancel(NOW.plusMinutes(1));

        assertThat(claim.getStatus()).isEqualTo(MerchantPlaceClaimStatus.CANCELED);
        assertThatThrownBy(() -> claim.approve(99L, "승인", NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
