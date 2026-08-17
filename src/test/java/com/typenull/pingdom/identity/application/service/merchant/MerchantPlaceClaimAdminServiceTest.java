package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimReviewRequest;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimType;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimAttachmentRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimReviewHistoryRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.query.place.duplicate.AdminMapPlaceDuplicateQueryService;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceClaimAdminServiceTest {

    @Mock private MerchantPlaceClaimRepository claimRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private MerchantPlaceInvitationRepository invitationRepository;
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private Clock clock;
    @Mock private MerchantPlaceClaimReviewHistoryRepository reviewHistoryRepository;
    @Mock private MerchantPlaceClaimAttachmentRepository attachmentRepository;
    @Mock private AdminMapPlaceDuplicateQueryService duplicateQueryService;
    @Mock private AdminRoleAuthorizationService authorizationService;

    @InjectMocks
    private MerchantPlaceClaimAdminService claimAdminService;

    @Test
    void approvalAtomicallyAssignsPlaceToEligibleClaimant() {
        Long claimId = 100L;
        Long userId = 1L;
        Long placeId = 10L;
        MerchantPlaceClaim claim = pendingClaim(claimId, userId, placeId);
        stubEligibleMerchantOwner(userId);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(placeId)).thenReturn(Optional.empty());

        var response = claimAdminService.review(99L, claimId, new MerchantPlaceClaimReviewRequest(true, "사업자 확인 완료"));

        ArgumentCaptor<MerchantOwnerPlace> placeCaptor = ArgumentCaptor.forClass(MerchantOwnerPlace.class);
        verify(ownerPlaceRepository).save(placeCaptor.capture());
        assertThat(placeCaptor.getValue().getPlaceId()).isEqualTo(placeId);
        assertThat(placeCaptor.getValue().getMerchantOwnerUserId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(MerchantPlaceClaimStatus.APPROVED);

        InOrder lockOrder = inOrder(claimRepository, userRepository, profileRepository, verificationRepository);
        lockOrder.verify(claimRepository).findById(claimId);
        lockOrder.verify(userRepository).findByIdForUpdate(userId);
        lockOrder.verify(profileRepository).findByUserIdForUpdate(userId);
        lockOrder.verify(verificationRepository).findByUserIdForUpdate(userId);
        lockOrder.verify(claimRepository).findByIdForUpdate(claimId);
    }

    @Test
    void approvalTransfersOwnershipWhenSnapshotMatchesCurrentOwner() {
        Long claimId = 100L;
        Long userId = 1L;
        Long placeId = 10L;
        Long previousOwnerUserId = 2L;
        MerchantPlaceClaim claim = pendingTransferClaim(claimId, userId, previousOwnerUserId, placeId);
        MerchantOwnerPlace ownerPlace = MerchantOwnerPlace.builder()
                .placeId(placeId)
                .merchantOwnerUserId(previousOwnerUserId)
                .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build();
        stubEligibleMerchantOwner(userId);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(placeId)).thenReturn(Optional.of(ownerPlace));
        when(memberRepository.findAllByPlaceId(placeId)).thenReturn(List.of());
        when(invitationRepository.findAllByPlaceIdAndStatus(any(), any())).thenReturn(List.of());
        when(memberRepository.findByPlaceIdAndUserIdForUpdate(placeId, userId)).thenReturn(Optional.empty());

        claimAdminService.review(
                99L,
                claimId,
                new MerchantPlaceClaimReviewRequest(true, "사업자 확인 완료")
        );

        assertThat(claim.getStatus()).isEqualTo(MerchantPlaceClaimStatus.APPROVED);
        assertThat(ownerPlace.getMerchantOwnerUserId()).isEqualTo(userId);
        verify(touristOfferRepository).closeAllByMerchantOwnerUserIdAndPlaceIdIn(
                previousOwnerUserId,
                Set.of(placeId),
                LocalDateTime.of(2026, 7, 15, 3, 0)
        );
        verify(ownerPlaceRepository, never()).save(any());
    }

    @Test
    void approvalRejectsTransferWhenOwnershipChangedAfterRequest() {
        Long claimId = 100L;
        Long userId = 1L;
        Long placeId = 10L;
        MerchantPlaceClaim claim = pendingTransferClaim(claimId, userId, 2L, placeId);
        stubEligibleMerchantOwner(userId);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(placeId)).thenReturn(Optional.of(MerchantOwnerPlace.builder()
                .placeId(placeId)
                .merchantOwnerUserId(3L)
                .createdAt(LocalDateTime.of(2026, 7, 14, 13, 0))
                .build()));

        assertThatThrownBy(() -> claimAdminService.review(
                99L, claimId, new MerchantPlaceClaimReviewRequest(true, "사업자 확인 완료")
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PLACE_OWNERSHIP_CHANGED));

        assertThat(claim.getStatus()).isEqualTo(MerchantPlaceClaimStatus.PENDING);
    }

    @Test
    void reviewRejectsStaleSubmissionVersionBeforeChangingClaim() {
        Long claimId = 100L;
        MerchantPlaceClaim claim = MerchantPlaceClaim.builder()
                .id(claimId)
                .version(3L)
                .merchantOwnerUserId(1L)
                .placeId(10L)
                .claimType(MerchantPlaceClaimType.INITIAL)
                .claimReason("사업자 확인")
                .status(MerchantPlaceClaimStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();
        when(claimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimAdminService.review(
                99L, claimId, new MerchantPlaceClaimReviewRequest(false, "최신 정보 확인", 2L)
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PLACE_OWNERSHIP_CHANGED));

        assertThat(claim.getStatus()).isEqualTo(MerchantPlaceClaimStatus.PENDING);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(reviewHistoryRepository, never()).save(any());
    }

    private MerchantPlaceClaim pendingClaim(Long claimId, Long userId, Long placeId) {
        return MerchantPlaceClaim.builder()
                .id(claimId)
                .merchantOwnerUserId(userId)
                .placeId(placeId)
                .claimType(MerchantPlaceClaimType.INITIAL)
                .claimReason("매장 운영자")
                .status(MerchantPlaceClaimStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();
    }

    private MerchantPlaceClaim pendingTransferClaim(
            Long claimId,
            Long userId,
            Long previousOwnerUserId,
            Long placeId
    ) {
        return MerchantPlaceClaim.builder()
                .id(claimId)
                .merchantOwnerUserId(userId)
                .placeId(placeId)
                .claimType(MerchantPlaceClaimType.OWNERSHIP_TRANSFER)
                .previousOwnerUserId(previousOwnerUserId)
                .claimReason("소유권 이전 요청")
                .status(MerchantPlaceClaimStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();
    }

    private void stubEligibleMerchantOwner(Long userId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);
        User user = User.builder().id(userId).role(UserRole.MERCHANT_OWNER).build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                now.minusDays(1)
        );
        profile.approve(99L, now.minusHours(1));
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                now.minusDays(1)
        );
        verification.review(99L, true, true, "확인 완료", now.minusHours(1));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
    }
}
