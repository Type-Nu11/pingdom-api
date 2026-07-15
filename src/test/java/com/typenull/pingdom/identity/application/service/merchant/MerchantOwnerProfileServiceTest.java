package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantOwnerPlaceRepository placeRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantOwnerProfileService profileService;

    @Test
    void businessNameChangeInvalidatesApprovedVerification() {
        Long userId = 1L;
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "기존 상호",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "기존 상호",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, true, true, "확인 완료", LocalDateTime.of(2026, 7, 14, 13, 0));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(placeRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)).thenReturn(List.of());
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        profileService.update(userId, new MerchantOwnerProfileRequest(
                "새 상호",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222"
        ));

        assertThat(verification.getBusinessName()).isEqualTo("새 상호");
        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
    }

    @Test
    void reapplicationWithChangedBusinessNameInvalidatesApprovedVerification() {
        Long userId = 1L;
        User user = User.builder().id(userId).role(UserRole.USER).build();
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "기존 상호",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        profile.reject(99L, LocalDateTime.of(2026, 7, 14, 13, 0));
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "기존 상호",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, true, true, "확인 완료", LocalDateTime.of(2026, 7, 14, 13, 0));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(placeRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)).thenReturn(List.of());
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        profileService.apply(userId, new MerchantOwnerProfileRequest(
                "새 상호",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222"
        ));

        assertThat(verification.getBusinessName()).isEqualTo("새 상호");
        assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
        assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
    }
}
