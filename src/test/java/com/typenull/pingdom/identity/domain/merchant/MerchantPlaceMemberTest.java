package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantPlaceMemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void createsOwnerAndChangesActiveMemberRole() {
        MerchantPlaceMember owner = MerchantPlaceMember.owner(10L, 1L, NOW);
        MerchantPlaceMember member = MerchantPlaceMember.create(10L, 2L, MerchantPlaceMemberRole.STAFF, 1L, NOW);

        assertThat(owner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        member.changeRole(MerchantPlaceMemberRole.MANAGER, NOW.plusMinutes(1));
        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.MANAGER);
    }

    @Test
    void rejectsOwnerRoleForInvitedMember() {
        assertThatThrownBy(() -> MerchantPlaceMember.create(10L, 2L, MerchantPlaceMemberRole.OWNER, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MerchantPlaceInvitation.pending(
                10L, 2L, 1L, MerchantPlaceMemberRole.OWNER, NOW.plusDays(1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiresInvitationBeforeAccepting() {
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.pending(
                10L, 2L, 1L, MerchantPlaceMemberRole.STAFF, NOW.plusHours(1), NOW);

        assertThatThrownBy(() -> invitation.accept(NOW.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
    }
}
