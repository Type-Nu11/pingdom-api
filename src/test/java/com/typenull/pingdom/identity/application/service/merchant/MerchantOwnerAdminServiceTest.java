package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
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
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantOwnerAdminService adminService;

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
        verify(userAccessStatusService).evict(userId);
    }
}
