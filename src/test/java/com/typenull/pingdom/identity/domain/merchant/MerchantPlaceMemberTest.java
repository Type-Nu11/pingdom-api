package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MerchantPlaceMemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    /**
     * 소유자는 OWNER로 생성하고 일반 STAFF 멤버는 MANAGER로 역할을 변경할 수 있는지 검증.
     */
    @Test
    void createsOwnerAndChangesMemberRole() {
        MerchantPlaceMember owner = MerchantPlaceMember.owner(10L, 1L, NOW);
        MerchantPlaceMember member = MerchantPlaceMember.create(10L, 2L, MerchantPlaceMemberRole.STAFF, 1L, NOW);

        assertThat(owner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        member.changeRole(MerchantPlaceMemberRole.MANAGER, NOW.plusMinutes(1));
        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.MANAGER);
    }

    @ParameterizedTest
    @EnumSource(MerchantPlaceMemberRole.class)
    void rejectsChangingCurrentOwnerRole(MerchantPlaceMemberRole requestedRole) {
        MerchantPlaceMember owner = MerchantPlaceMember.owner(10L, 1L, NOW);

        assertThatThrownBy(() -> owner.changeRole(requestedRole, NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(owner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        assertThat(owner.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void preservesDedicatedOwnershipTransferMethods() {
        MerchantPlaceMember previousOwner = MerchantPlaceMember.owner(10L, 1L, NOW);
        MerchantPlaceMember nextOwner = MerchantPlaceMember.create(
                10L, 2L, MerchantPlaceMemberRole.MANAGER, 1L, NOW);

        previousOwner.revoke(NOW.plusMinutes(1));
        nextOwner.promoteToOwner(NOW.plusMinutes(1));

        assertThat(previousOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
        assertThat(nextOwner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(nextOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);

        nextOwner.revoke(NOW.plusMinutes(2));
        previousOwner.restoreAsOwner(1L, NOW.plusMinutes(2));

        assertThat(previousOwner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(previousOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        assertThat(previousOwner.getInvitedBy()).isEqualTo(1L);
        assertThat(previousOwner.getUpdatedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(nextOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
    }

    /**
     * 일반 멤버 생성과 초대에 OWNER를 지정하면 모두 IllegalArgumentException으로 거절하는지 검증.
     */
    @Test
    void rejectsInvitedOwnerRole() {
        assertThatThrownBy(() -> MerchantPlaceMember.create(10L, 2L, MerchantPlaceMemberRole.OWNER, 1L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MerchantPlaceInvitation.pending(
                10L, 2L, 1L, MerchantPlaceMemberRole.OWNER, NOW.plusDays(1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 해제된 멤버 재활성화가 같은 객체의 ACTIVE·MANAGER·새 초대자 ID를 반영하는지 검증.
     */
    @Test
    void reactivatesRevokedMember() {
        MerchantPlaceMember member = MerchantPlaceMember.create(
                10L, 2L, MerchantPlaceMemberRole.STAFF, 1L, NOW);
        member.revoke(NOW.plusMinutes(1));

        member.reactivate(MerchantPlaceMemberRole.MANAGER, 3L, NOW.plusMinutes(2));

        assertThat(member.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.MANAGER);
        assertThat(member.getInvitedBy()).isEqualTo(3L);
    }

    /**
     * 만료 시각과 같은 시각에 초대를 수락하면 IllegalStateException과 EXPIRED 상태가 반영되는지 검증.
     */
    @Test
    void expiresInvitationAtAcceptanceBoundary() {
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.pending(
                10L, 2L, 1L, MerchantPlaceMemberRole.STAFF, NOW.plusHours(1), NOW);

        assertThatThrownBy(() -> invitation.accept(NOW.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
    }
}
