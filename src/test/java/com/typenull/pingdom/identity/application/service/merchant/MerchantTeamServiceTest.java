package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
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

class MerchantTeamServiceTest {

    private final MerchantPlaceMemberRepository memberRepository = mock(MerchantPlaceMemberRepository.class);
    private final MerchantPlaceInvitationRepository invitationRepository = mock(MerchantPlaceInvitationRepository.class);
    private final MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
    private final MerchantTeamService service = new MerchantTeamService(
            memberRepository,
            invitationRepository,
            ownerPlaceRepository,
            userRepository,
            clock
    );

    @Test
    void mapsExpiredInvitationAcceptanceToGoneError() {
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
