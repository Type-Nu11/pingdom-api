package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamMemberResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInvitationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInviteRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamRoleUpdateRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class MerchantTeamServiceTest {

    private final MerchantPlaceMemberRepository memberRepository = mock(MerchantPlaceMemberRepository.class);
    private final MerchantPlaceInvitationRepository invitationRepository = mock(MerchantPlaceInvitationRepository.class);
    private final MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
    private final MerchantPlaceCapabilityPolicy capabilityPolicy = new MerchantPlaceCapabilityPolicy(
            memberRepository, ownerPlaceRepository, userRepository, clock);
    private final MerchantTeamService service = new MerchantTeamService(
            memberRepository,
            invitationRepository,
            ownerPlaceRepository,
            userRepository,
            capabilityPolicy,
            clock
    );

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void expiresPreviousInvitationBeforeSavingReinvitation(int expiryOffsetSeconds) {
        allowInviting();
        LocalDateTime now = LocalDateTime.now(clock);
        MerchantPlaceInvitation previous = invitationExpiringAt(now.plusSeconds(expiryOffsetSeconds));
        when(invitationRepository.findByPlaceIdAndInviteeUserIdAndStatusForUpdate(
                1L, 10L, MerchantPlaceInvitationStatus.PENDING)).thenReturn(Optional.of(previous));

        MerchantTeamInvitationResponse response = service.invite(
                20L, 1L, new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.STAFF, null));

        assertThat(previous.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
        assertThat(previous.getRespondedAt()).isNull();
        assertThat(response.status()).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(now.plusDays(7));
        assertThat(response.createdAt()).isEqualTo(now);
        InOrder order = inOrder(ownerPlaceRepository, invitationRepository);
        order.verify(ownerPlaceRepository).findByPlaceIdForUpdate(1L);
        order.verify(invitationRepository).findByPlaceIdAndInviteeUserIdAndStatusForUpdate(
                1L, 10L, MerchantPlaceInvitationStatus.PENDING);
        order.verify(invitationRepository).flush();
        order.verify(invitationRepository).save(any(MerchantPlaceInvitation.class));
    }

    @Test
    void invitesWithoutPendingInvitationAndKeepsRequestedExpiration() {
        allowInviting();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusHours(1);

        MerchantTeamInvitationResponse response = service.invite(
                20L, 1L, new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.MANAGER, expiresAt));

        assertThat(response.role()).isEqualTo(MerchantPlaceMemberRole.MANAGER);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(invitationRepository, never()).flush();
    }

    @Test
    void rejectsReinvitationWhilePreviousInvitationIsStillValid() {
        allowInviting();
        MerchantPlaceInvitation previous = invitationExpiringAt(LocalDateTime.now(clock).plusSeconds(1));
        when(invitationRepository.findByPlaceIdAndInviteeUserIdAndStatusForUpdate(
                1L, 10L, MerchantPlaceInvitationStatus.PENDING)).thenReturn(Optional.of(previous));

        assertInvitationRejected(new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.STAFF, null),
                MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_ALREADY_EXISTS);

        assertThat(previous.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        verify(invitationRepository, never()).flush();
        verify(invitationRepository, never()).save(any(MerchantPlaceInvitation.class));
    }

    @Test
    void invalidNewExpirationDoesNotExpirePreviousInvitation() {
        allowInviting();
        LocalDateTime now = LocalDateTime.now(clock);
        MerchantPlaceInvitation previous = invitationExpiringAt(now.minusSeconds(1));
        when(invitationRepository.findByPlaceIdAndInviteeUserIdAndStatusForUpdate(
                1L, 10L, MerchantPlaceInvitationStatus.PENDING)).thenReturn(Optional.of(previous));

        assertInvitationRejected(new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.STAFF, now),
                MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_INVALID_EXPIRATION);

        assertThat(previous.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        verify(invitationRepository, never()).flush();
        verify(invitationRepository, never()).save(any(MerchantPlaceInvitation.class));
    }

    @Test
    void unauthorizedActorCannotExpirePendingInvitation() {
        allowActiveActor();
        MerchantPlaceMember staff = MerchantPlaceMember.create(
                1L, 20L, MerchantPlaceMemberRole.STAFF, 30L, LocalDateTime.now(clock));
        when(memberRepository.findByPlaceIdAndUserId(1L, 20L)).thenReturn(Optional.of(staff));

        assertInvitationRejected(new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.STAFF, null),
                MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);

        verify(invitationRepository, never()).findByPlaceIdAndInviteeUserIdAndStatusForUpdate(any(), any(), any());
        verify(invitationRepository, never()).flush();
    }

    @Test
    void activeMemberCannotBeReinvited() {
        allowInviting();
        MerchantPlaceMember member = MerchantPlaceMember.create(
                1L, 10L, MerchantPlaceMemberRole.STAFF, 20L, LocalDateTime.now(clock));
        when(memberRepository.findByPlaceIdAndUserId(1L, 10L)).thenReturn(Optional.of(member));

        assertInvitationRejected(new MerchantTeamInviteRequest(10L, MerchantPlaceMemberRole.STAFF, null),
                MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_ALREADY_EXISTS);

        verify(invitationRepository, never()).findByPlaceIdAndInviteeUserIdAndStatusForUpdate(any(), any(), any());
        verify(invitationRepository, never()).flush();
    }

    private void allowInviting() {
        allowOwner();
        User invitee = mock(User.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(invitee));
        when(invitationRepository.save(any(MerchantPlaceInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MerchantPlaceInvitation invitationExpiringAt(LocalDateTime expiresAt) {
        return MerchantPlaceInvitation.pending(
                1L, 10L, 20L, MerchantPlaceMemberRole.STAFF, expiresAt, LocalDateTime.now(clock).minusDays(1));
    }

    private void assertInvitationRejected(MerchantTeamInviteRequest request, MerchantOwnerErrorCode code) {
        assertThatThrownBy(() -> service.invite(20L, 1L, request))
                .isInstanceOfSatisfying(MerchantOwnerException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @CsvSource({"STAFF, MANAGER", "MANAGER, STAFF"})
    void ownerChangesNonOwnerRole(MerchantPlaceMemberRole originalRole, MerchantPlaceMemberRole requestedRole) {
        allowOwner();
        LocalDateTime createdAt = LocalDateTime.now(clock).minusDays(1);
        MerchantPlaceMember member = MerchantPlaceMember.create(1L, 30L, originalRole, 20L, createdAt);
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));

        MerchantTeamMemberResponse response = service.updateRole(
                20L, 1L, 100L, new MerchantTeamRoleUpdateRequest(requestedRole));

        assertThat(response.role()).isEqualTo(requestedRole);
        assertThat(member.getRole()).isEqualTo(requestedRole);
        assertThat(member.getUpdatedAt()).isEqualTo(LocalDateTime.now(clock));
        verify(ownerPlaceRepository).findByPlaceIdForUpdate(1L);
    }

    @ParameterizedTest
    @EnumSource(value = MerchantPlaceMemberRole.class, names = {"MANAGER", "STAFF"})
    void rejectsNonOwnerRoleManagement(MerchantPlaceMemberRole actorRole) {
        allowActiveActor();
        when(memberRepository.findByPlaceIdAndUserId(1L, 20L)).thenReturn(Optional.of(
                MerchantPlaceMember.create(1L, 20L, actorRole, 10L, LocalDateTime.now(clock))));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.STAFF,
                MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);

        verify(memberRepository, never()).findById(anyLong());
    }

    @ParameterizedTest
    @CsvSource({"20, STAFF", "20, MANAGER", "30, STAFF", "30, MANAGER"})
    void rejectsOwnerRoleChangeEvenByOwner(Long targetUserId, MerchantPlaceMemberRole requestedRole) {
        allowOwner();
        LocalDateTime createdAt = LocalDateTime.now(clock).minusDays(1);
        MerchantPlaceMember owner = MerchantPlaceMember.owner(1L, targetUserId, createdAt);
        when(memberRepository.findById(100L)).thenReturn(Optional.of(owner));

        assertRoleUpdateRejected(100L, requestedRole, MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);

        assertThat(owner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        assertThat(owner.getUpdatedAt()).isEqualTo(createdAt);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void rejectsWithdrawnOrBannedOwner(boolean withdrawn, boolean banned) {
        User actor = allowOwner();
        when(actor.isWithdrawn()).thenReturn(withdrawn);
        when(actor.isCurrentlyBanned(LocalDateTime.now(clock))).thenReturn(banned);

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.STAFF,
                MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);

        verify(memberRepository, never()).findById(anyLong());
    }

    @Test
    void rejectsRevokedOwnerWithoutFallingBackToOwnership() {
        allowActiveActor();
        MerchantPlaceMember owner = MerchantPlaceMember.owner(1L, 20L, LocalDateTime.now(clock));
        owner.revoke(LocalDateTime.now(clock));
        when(memberRepository.findByPlaceIdAndUserId(1L, 20L)).thenReturn(Optional.of(owner));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.STAFF,
                MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);

        verify(ownerPlaceRepository, never()).findById(anyLong());
        verify(memberRepository, never()).findById(anyLong());
    }

    @Test
    void currentOwnerWithoutTeamRowCanChangeMemberRole() {
        allowActiveActor();
        when(ownerPlaceRepository.findById(1L)).thenReturn(Optional.of(MerchantOwnerPlace.builder()
                .placeId(1L).merchantOwnerUserId(20L).build()));
        MerchantPlaceMember member = MerchantPlaceMember.create(
                1L, 30L, MerchantPlaceMemberRole.STAFF, 20L, LocalDateTime.now(clock));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));

        service.updateRole(20L, 1L, 100L, new MerchantTeamRoleUpdateRequest(MerchantPlaceMemberRole.MANAGER));

        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.MANAGER);
    }

    @Test
    void rejectsMemberFromAnotherPlace() {
        allowOwner();
        MerchantPlaceMember member = MerchantPlaceMember.create(
                2L, 30L, MerchantPlaceMemberRole.STAFF, 20L, LocalDateTime.now(clock));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.MANAGER,
                MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_FOUND);

        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.STAFF);
    }

    @Test
    void rejectsMissingMember() {
        allowOwner();

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.MANAGER,
                MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_FOUND);
    }

    @Test
    void preservesInactiveMemberError() {
        allowOwner();
        LocalDateTime createdAt = LocalDateTime.now(clock).minusDays(1);
        MerchantPlaceMember member = MerchantPlaceMember.create(
                1L, 30L, MerchantPlaceMemberRole.STAFF, 20L, createdAt);
        member.revoke(createdAt);
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.MANAGER,
                MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_ACTIVE);

        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.STAFF);
        assertThat(member.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
        assertThat(member.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    void preservesInactiveOwnerErrorAfterOwnershipTransfer() {
        allowOwner();
        LocalDateTime revokedAt = LocalDateTime.now(clock).minusDays(1);
        MerchantPlaceMember previousOwner = MerchantPlaceMember.owner(1L, 30L, revokedAt);
        previousOwner.revoke(revokedAt);
        when(memberRepository.findById(100L)).thenReturn(Optional.of(previousOwner));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.STAFF,
                MerchantOwnerErrorCode.MERCHANT_TEAM_MEMBER_NOT_ACTIVE);

        assertThat(previousOwner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(previousOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
        assertThat(previousOwner.getUpdatedAt()).isEqualTo(revokedAt);
    }

    @Test
    void preservesOwnerAssignmentError() {
        allowOwner();
        MerchantPlaceMember member = MerchantPlaceMember.create(
                1L, 30L, MerchantPlaceMemberRole.STAFF, 20L, LocalDateTime.now(clock));
        when(memberRepository.findById(100L)).thenReturn(Optional.of(member));

        assertRoleUpdateRejected(100L, MerchantPlaceMemberRole.OWNER,
                MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_INVALID_ROLE);

        assertThat(member.getRole()).isEqualTo(MerchantPlaceMemberRole.STAFF);
    }

    private User allowActiveActor() {
        User actor = mock(User.class);
        when(actor.getId()).thenReturn(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(actor));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(1L)).thenReturn(Optional.of(mock(MerchantOwnerPlace.class)));
        return actor;
    }

    private User allowOwner() {
        User actor = allowActiveActor();
        when(memberRepository.findByPlaceIdAndUserId(1L, 20L)).thenReturn(Optional.of(
                MerchantPlaceMember.owner(1L, 20L, LocalDateTime.now(clock))));
        return actor;
    }

    private void assertRoleUpdateRejected(Long memberId, MerchantPlaceMemberRole role, MerchantOwnerErrorCode code) {
        assertThatThrownBy(() -> service.updateRole(20L, 1L, memberId, new MerchantTeamRoleUpdateRequest(role)))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    /**
     * 만료 수락의 오류와 메모리 상태만 검증하며, 트랜잭션 종료 후 저장 결과는 DB 통합 검증에서 확인.
     */
    @Test
    void rejectsExpiredInvitationAcceptance() {
        LocalDateTime now = LocalDateTime.now(clock);
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.pending(
                1L,
                10L,
                20L,
                MerchantPlaceMemberRole.STAFF,
                now.minusMinutes(1),
                now.minusDays(1)
        );
        User invitee = mock(User.class);

        when(invitationRepository.findById(100L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invitation));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(1L)).thenReturn(Optional.of(mock(MerchantOwnerPlace.class)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(invitee));
        when(invitee.isWithdrawn()).thenReturn(false);
        when(invitee.isCurrentlyBanned(now)).thenReturn(false);
        when(memberRepository.findByPlaceIdAndUserIdForUpdate(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation(10L, 100L))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_EXPIRED)
                );
        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
    }
}
