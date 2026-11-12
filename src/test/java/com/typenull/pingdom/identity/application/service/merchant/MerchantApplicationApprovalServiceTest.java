package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantApplicationApprovalServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 0, 0);
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private MerchantPlaceInvitationRepository invitationRepository;
    @InjectMocks private MerchantApplicationApprovalService service;

    @Test
    void preparesPendingProfileAndVerificationFromValues() {
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.empty());
        when(verificationRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.empty());

        service.prepare(data(), NOW);

        ArgumentCaptor<MerchantOwnerProfile> profile = ArgumentCaptor.forClass(MerchantOwnerProfile.class);
        ArgumentCaptor<MerchantVerification> verification = ArgumentCaptor.forClass(MerchantVerification.class);
        verify(profileRepository).save(profile.capture());
        verify(verificationRepository).save(verification.capture());
        assertThat(profile.getValue().getStatus()).isEqualTo(MerchantOwnerStatus.PENDING);
        assertThat(profile.getValue().getBusinessName()).isEqualTo("핑덤");
        assertThat(profile.getValue().getContactEmail()).isEqualTo("owner@pingdom.test");
        assertThat(verification.getValue().getLegalName()).isEqualTo("홍길동");
        assertThat(verification.getValue().getEncryptedBusinessRegistrationNumber()).isEqualTo("encrypted");
        assertThat(verification.getValue().getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        verifyNoInteractions(userRepository, ownerPlaceRepository, memberRepository, invitationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = MerchantOwnerStatus.class, names = {"REJECTED", "REVOKED"})
    void reappliesInactiveProfileAndRejectedVerification(MerchantOwnerStatus status) {
        MerchantOwnerProfile profile = MerchantOwnerProfile.builder().userId(10L).status(status)
                .businessName("이전 상호").reviewedBy(99L).reviewReason("이전 심사").reviewedAt(NOW.minusDays(1)).build();
        MerchantVerification verification = MerchantVerification.pending(10L, "이전 이름", "이전 상호", "old", NOW.minusDays(1));
        verification.review(99L, true, false, "서류 보완", NOW.minusDays(1));
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(verification));

        service.prepare(data(), NOW);

        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.PENDING);
        assertThat(profile.getBusinessName()).isEqualTo("핑덤");
        assertThat(profile.getReviewedBy()).isNull();
        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getReviewedBy()).isNull();
    }

    @Test
    void refusesActiveBusinessNameChangeBeforeVerificationWrites() {
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(10L, "기존 상호", "표시명", "소개", "old@test.com", "010", NOW);
        profile.approve(99L, NOW);
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.prepare(data(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활성 사업자의 상호 변경은 기존 사업자 정보 변경 절차를 사용해야 합니다.");

        assertThat(profile.getBusinessName()).isEqualTo("기존 상호");
        verifyNoInteractions(verificationRepository, userRepository);
    }

    @Test
    void activatesLockedProfileAndUserThenApprovesVerification() {
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(10L, "핑덤", "표시명", "소개", "owner@test.com", "010", NOW);
        MerchantVerification verification = MerchantVerification.pending(10L, "홍길동", "핑덤", "encrypted", NOW);
        User user = User.builder().id(10L).role(UserRole.USER).build();
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(verification));

        service.activate(10L, 99L, "승인", NOW);
        service.approveVerification(10L, 99L, "승인", NOW);

        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.ACTIVE);
        assertThat(profile.getReviewReason()).isEqualTo("승인");
        assertThat(user.getRole()).isEqualTo(UserRole.MERCHANT_OWNER);
        assertThat(verification.isFullyApproved()).isTrue();
        assertThat(verification.getReviewedBy()).isEqualTo(99L);
    }

    @ParameterizedTest
    @EnumSource(value = MerchantPlaceMemberStatus.class, names = {"ACTIVE", "REVOKED"})
    void transfersOwnershipAndRestoresOrPromotesNewOwner(MerchantPlaceMemberStatus newOwnerStatus) {
        MerchantOwnerPlace ownership = MerchantOwnerPlace.builder().placeId(30L).merchantOwnerUserId(20L).createdAt(NOW).build();
        MerchantPlaceMember oldOwner = MerchantPlaceMember.owner(30L, 20L, NOW);
        MerchantPlaceMember teammate = MerchantPlaceMember.create(30L, 21L, MerchantPlaceMemberRole.MANAGER, 20L, NOW);
        MerchantPlaceMember newOwner = MerchantPlaceMember.create(30L, 10L, MerchantPlaceMemberRole.STAFF, 20L, NOW);
        if (newOwnerStatus == MerchantPlaceMemberStatus.REVOKED) {
            newOwner.revoke(NOW.minusMinutes(1));
        }
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.pending(
                30L, 22L, 20L, MerchantPlaceMemberRole.STAFF, NOW.plusDays(1), NOW);
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.of(ownership));
        when(memberRepository.findAllByPlaceId(30L)).thenReturn(List.of(oldOwner, teammate, newOwner));
        when(invitationRepository.findAllByPlaceIdAndStatus(30L, MerchantPlaceInvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
        when(memberRepository.findByPlaceIdAndUserIdForUpdate(30L, 10L)).thenReturn(Optional.of(newOwner));

        Long previousOwner = service.transferPlaceOwnership(30L, 20L, 10L, NOW);

        assertThat(previousOwner).isEqualTo(20L);
        assertThat(ownership.getMerchantOwnerUserId()).isEqualTo(10L);
        assertThat(oldOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
        assertThat(teammate.getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.REVOKED);
        assertThat(newOwner.getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        assertThat(newOwner.getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        verify(memberRepository).save(newOwner);
        verifyNoInteractions(profileRepository, verificationRepository, userRepository);
    }

    @Test
    void refusesChangedOwnerBeforeTouchingTeam() {
        MerchantOwnerPlace ownership = MerchantOwnerPlace.builder().placeId(30L).merchantOwnerUserId(21L).build();
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.of(ownership));

        assertThatThrownBy(() -> service.transferPlaceOwnership(30L, 20L, 10L, NOW))
                .isInstanceOfSatisfying(MerchantOwnerException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PLACE_OWNERSHIP_CHANGED));

        assertThat(ownership.getMerchantOwnerUserId()).isEqualTo(21L);
        verifyNoInteractions(memberRepository, invitationRepository);
    }

    @Test
    void assignsUnownedPlaceWithoutRevokingUnrelatedTeam() {
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.empty());
        when(memberRepository.findByPlaceIdAndUserIdForUpdate(30L, 10L)).thenReturn(Optional.empty());

        assertThat(service.transferPlaceOwnership(30L, null, 10L, NOW)).isNull();

        ArgumentCaptor<MerchantOwnerPlace> ownership = ArgumentCaptor.forClass(MerchantOwnerPlace.class);
        ArgumentCaptor<MerchantPlaceMember> owner = ArgumentCaptor.forClass(MerchantPlaceMember.class);
        verify(ownerPlaceRepository).save(ownership.capture());
        verify(memberRepository).save(owner.capture());
        assertThat(ownership.getValue().getMerchantOwnerUserId()).isEqualTo(10L);
        assertThat(owner.getValue().getUserId()).isEqualTo(10L);
        assertThat(owner.getValue().getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
        verifyNoInteractions(invitationRepository);
    }

    private MerchantApplicationApprovalService.MerchantData data() {
        return new MerchantApplicationApprovalService.MerchantData(
                10L, "홍길동", "핑덤", "encrypted", "표시명", "소개", "owner@pingdom.test", "01012345678");
    }
}
