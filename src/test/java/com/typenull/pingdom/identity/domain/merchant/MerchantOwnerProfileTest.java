package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantOwnerProfileTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 12, 0);

    @Test
    void pendingProfileCanBeApprovedAndRevoked() {
        MerchantOwnerProfile profile = pendingProfile();

        profile.approve(99L, NOW.plusMinutes(1));
        profile.revoke(99L, NOW.plusMinutes(2));

        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.REVOKED);
        assertThat(profile.getReviewedBy()).isEqualTo(99L);
    }

    @Test
    void rejectedProfileCanBeReapplied() {
        MerchantOwnerProfile profile = pendingProfile();
        profile.reject(99L, "서류 보완 필요", NOW.plusMinutes(1));

        profile.reapply(
                "새 상호",
                "새 표시명",
                "재신청",
                "new@example.com",
                "010-2222-3333",
                NOW.plusMinutes(2)
        );

        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.PENDING);
        assertThat(profile.getReviewedBy()).isNull();
        assertThat(profile.getReviewReason()).isNull();
        assertThat(profile.getBusinessName()).isEqualTo("새 상호");
    }

    @Test
    void activeProfileCannotBeRejected() {
        MerchantOwnerProfile profile = pendingProfile();
        profile.approve(99L, NOW.plusMinutes(1));

        assertThatThrownBy(() -> profile.reject(99L, NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private MerchantOwnerProfile pendingProfile() {
        return MerchantOwnerProfile.pending(
                1L,
                "핑덤 카페",
                "핑덤 사장님",
                "관광객 환영",
                "owner@example.com",
                "010-1111-2222",
                NOW
        );
    }
}
