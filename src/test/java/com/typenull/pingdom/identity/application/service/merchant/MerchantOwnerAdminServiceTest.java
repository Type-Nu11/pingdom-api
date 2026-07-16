package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantOwnerAdminService adminService;

    @Test
    void withdrawnUserCannotBeApproved() {
        Long userId = 1L;
        User withdrawnUser = User.builder()
                .id(userId)
                .status(UserStatus.WITHDRAWN)
                .build();
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> adminService.approve(
                99L,
                userId,
                new MerchantOwnerReviewRequest("승인", Set.of())
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.USER_ACCOUNT_NOT_ELIGIBLE)
        );

        verify(profileRepository, never()).findByUserIdForUpdate(userId);
    }

    @Test
    void currentlyBannedUserCannotBeApproved() {
        Long userId = 1L;
        User bannedUser = User.builder()
                .id(userId)
                .banned(true)
                .banType(UserBanType.PERMANENT)
                .build();
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(bannedUser));

        assertThatThrownBy(() -> adminService.approve(
                99L,
                userId,
                new MerchantOwnerReviewRequest("승인", Set.of())
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.USER_ACCOUNT_NOT_ELIGIBLE)
        );

        verify(profileRepository, never()).findByUserIdForUpdate(userId);
    }

    @Test
    void revokeImmediatelyRemovesCurrentRoleRefreshTokenAndPlaceLinks() {
        Long adminUserId = 99L;
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
        User owner = User.builder()
                .id(userId)
                .role(UserRole.MERCHANT_OWNER)
                .refreshToken("refresh-token")
                .build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                now.minusDays(1)
        );
        profile.approve(adminUserId, now.minusHours(1));

        when(clock.instant()).thenReturn(Instant.parse("2026-07-13T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(owner));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)).thenReturn(List.of());

        var response = adminService.revoke(
                adminUserId,
                userId,
                new MerchantOwnerReviewRequest("계약 종료", Set.of())
        );

        assertThat(response.status()).isEqualTo(MerchantOwnerStatus.REVOKED);
        assertThat(owner.getRole()).isEqualTo(UserRole.USER);
        assertThat(owner.getRefreshToken()).isNull();
        verify(ownerPlaceRepository).deleteAllByMerchantOwnerUserId(userId);
        verify(touristOfferRepository).closeAllByMerchantOwnerUserId(
                userId,
                LocalDateTime.of(2026, 7, 13, 3, 0)
        );
        verify(userAccessStatusService).evict(userId);
    }

    @Test
    void rejectImmediatelyClosesMerchantOffers() {
        Long adminUserId = 99L;
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 3, 0);
        User owner = User.builder()
                .id(userId)
                .role(UserRole.MERCHANT_OWNER)
                .refreshToken("refresh-token")
                .build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                now.minusDays(1)
        );
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(owner));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)).thenReturn(List.of());

        var response = adminService.reject(
                adminUserId,
                userId,
                new MerchantOwnerReviewRequest("서류 미흡", Set.of())
        );

        assertThat(response.status()).isEqualTo(MerchantOwnerStatus.REJECTED);
        verify(touristOfferRepository).closeAllByMerchantOwnerUserId(userId, now);
    }

    @Test
    void replacingOwnedPlacesClosesOffersForRemovedPlaces() {
        Long adminUserId = 99L;
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 3, 0);
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                now.minusDays(1)
        );
        profile.approve(adminUserId, now.minusHours(1));
        MerchantOwnerPlace firstPlace = MerchantOwnerPlace.builder()
                .placeId(10L)
                .merchantOwnerUserId(userId)
                .createdAt(now.minusDays(1))
                .build();
        MerchantOwnerPlace secondPlace = MerchantOwnerPlace.builder()
                .placeId(20L)
                .merchantOwnerUserId(userId)
                .createdAt(now.minusDays(1))
                .build();
        stubCurrentTime();
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId))
                .thenReturn(List.of(firstPlace, secondPlace))
                .thenReturn(List.of(secondPlace));
        when(mapPlaceRepository.findAllByIdInForUpdate(List.of(20L)))
                .thenReturn(List.of(mock(com.typenull.pingdom.place.domain.place.core.MapPlace.class)));
        when(ownerPlaceRepository.findAllByPlaceIdIn(List.of(20L))).thenReturn(List.of(secondPlace));

        adminService.replacePlaces(
                adminUserId,
                userId,
                new MerchantOwnerPlaceUpdateRequest(Set.of(20L), "운영 장소 변경")
        );

        verify(touristOfferRepository).closeAllByMerchantOwnerUserIdAndPlaceIdIn(
                userId,
                Set.of(10L),
                now
        );
    }

    @Test
    void merchantOwnerCannotBeApprovedUntilBothVerificationsAreApproved() {
        Long userId = 1L;
        User user = User.builder().id(userId).role(UserRole.USER).build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, true, false, "사업자 정보 불일치", LocalDateTime.of(2026, 7, 14, 13, 0));
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> adminService.approve(
                99L,
                userId,
                new MerchantOwnerReviewRequest("승인", Set.of())
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.VERIFICATION_REQUIRED)
        );

        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void merchantOwnerCanBeApprovedAfterBothVerificationsAreApproved() {
        Long userId = 1L;
        User user = User.builder().id(userId).role(UserRole.USER).build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, true, true, "확인 완료", LocalDateTime.of(2026, 7, 14, 13, 0));
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)).thenReturn(List.of());

        var response = adminService.approve(
                99L,
                userId,
                new MerchantOwnerReviewRequest("승인", Set.of())
        );

        assertThat(response.status()).isEqualTo(MerchantOwnerStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(UserRole.MERCHANT_OWNER);
    }

    @Test
    void merchantOwnerCannotBeApprovedWhenVerifiedBusinessNameDiffers() {
        Long userId = 1L;
        User user = User.builder().id(userId).role(UserRole.USER).build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "현재 상호",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "검증 당시 상호",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, true, true, "확인 완료", LocalDateTime.of(2026, 7, 14, 13, 0));
        stubCurrentTime();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> adminService.approve(
                99L,
                userId,
                new MerchantOwnerReviewRequest("승인", Set.of())
        )).isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.VERIFICATION_REQUIRED)
        );
    }

    private void stubCurrentTime() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-13T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
}
