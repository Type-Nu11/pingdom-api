package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimType;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceClaimServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceClaimRepository claimRepository;
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantPlaceClaimService claimService;

    @Test
    void eligibleMerchantOwnerCanCreateClaimForUnassignedPlace() {
        Long userId = 1L;
        Long placeId = 10L;
        stubEligibleMerchantOwner(userId);
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(ownerPlaceRepository.findById(placeId)).thenReturn(Optional.empty());
        when(claimRepository.existsByPlaceIdAndStatus(placeId, MerchantPlaceClaimStatus.PENDING)).thenReturn(false);
        when(claimRepository.save(any(MerchantPlaceClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = claimService.create(userId, new MerchantPlaceClaimRequest(placeId, "  해당 매장을 운영합니다.  "));

        ArgumentCaptor<MerchantPlaceClaim> claimCaptor = ArgumentCaptor.forClass(MerchantPlaceClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getMerchantOwnerUserId()).isEqualTo(userId);
        assertThat(claimCaptor.getValue().getClaimReason()).isEqualTo("해당 매장을 운영합니다.");
        assertThat(claimCaptor.getValue().getClaimType()).isEqualTo(MerchantPlaceClaimType.INITIAL);
        assertThat(response.status()).isEqualTo(MerchantPlaceClaimStatus.PENDING);
    }

    @Test
    void duplicatePendingClaimForSamePlaceIsRejected() {
        Long userId = 1L;
        Long placeId = 10L;
        stubEligibleMerchantOwner(userId);
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(claimRepository.existsByPlaceIdAndStatus(placeId, MerchantPlaceClaimStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> claimService.create(userId, new MerchantPlaceClaimRequest(placeId, "매장 운영자")))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PLACE_CLAIM_ALREADY_PENDING)
                );

        verify(claimRepository, never()).save(any());
    }

    @Test
    void assignedPlaceCreatesOwnershipTransferClaimWithOwnerSnapshot() {
        Long userId = 1L;
        Long previousOwnerUserId = 2L;
        Long placeId = 10L;
        stubEligibleMerchantOwner(userId);
        when(mapPlaceRepository.findByIdForUpdate(placeId)).thenReturn(Optional.of(mock(MapPlace.class)));
        when(claimRepository.existsByPlaceIdAndStatus(placeId, MerchantPlaceClaimStatus.PENDING)).thenReturn(false);
        when(ownerPlaceRepository.findById(placeId)).thenReturn(Optional.of(MerchantOwnerPlace.builder()
                .placeId(placeId)
                .merchantOwnerUserId(previousOwnerUserId)
                .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build()));
        when(claimRepository.save(any(MerchantPlaceClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = claimService.create(userId, new MerchantPlaceClaimRequest(placeId, "소유권 이전 요청"));

        ArgumentCaptor<MerchantPlaceClaim> claimCaptor = ArgumentCaptor.forClass(MerchantPlaceClaim.class);
        verify(claimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getPreviousOwnerUserId()).isEqualTo(previousOwnerUserId);
        assertThat(response.claimType()).isEqualTo(MerchantPlaceClaimType.OWNERSHIP_TRANSFER);
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
