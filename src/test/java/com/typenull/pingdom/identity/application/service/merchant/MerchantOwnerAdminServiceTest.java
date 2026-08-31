package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerAdminServiceTest {

    private static final Long ADMIN_USER_ID = 99L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Mock private UserRepository userRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Clock clock;
    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private PlaceRegistrationApplicationRepository applicationRepository;

    @InjectMocks
    private MerchantOwnerAdminService service;

    @Test
    void approvesPendingStandaloneProfileAndStoresReviewMetadata() {
        User user = User.builder().id(USER_ID).role(UserRole.USER).build();
        MerchantOwnerProfile profile = pendingProfile();
        stubNow();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(profile));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(USER_ID)).thenReturn(List.of());

        var response = service.approve(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest("서류 확인"));

        assertThat(response.status()).isEqualTo(MerchantOwnerStatus.ACTIVE);
        assertThat(response.reviewedBy()).isEqualTo(ADMIN_USER_ID);
        assertThat(response.reviewReason()).isEqualTo("서류 확인");
        assertThat(user.getRole()).isEqualTo(UserRole.MERCHANT_OWNER);
        verify(userAccessStatusService).evict(USER_ID);
    }

    @Test
    void rejectsPendingStandaloneProfileWithRequiredReason() {
        User user = User.builder().id(USER_ID).role(UserRole.USER).build();
        MerchantOwnerProfile profile = pendingProfile();
        stubNow();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(profile));
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(USER_ID)).thenReturn(List.of());

        var response = service.reject(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest("사업자 정보가 부족합니다"));

        assertThat(response.status()).isEqualTo(MerchantOwnerStatus.REJECTED);
        assertThat(response.reviewReason()).isEqualTo("사업자 정보가 부족합니다");
        verify(touristOfferRepository).closeAllByMerchantOwnerUserId(
                USER_ID,
                LocalDateTime.of(2026, 8, 31, 3, 0)
        );
        verify(ownerPlaceRepository).deleteAllByMerchantOwnerUserId(USER_ID);
    }

    @Test
    void rejectsBlankReviewReason() {
        assertThatThrownBy(() -> service.reject(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest(" ")))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.INVALID_REVIEW_REASON));
    }

    @Test
    void blocksDirectReviewWhenUnifiedApplicationIsPending() {
        User user = User.builder().id(USER_ID).role(UserRole.USER).build();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(applicationRepository.existsByApplicantUserIdAndStatus(
                USER_ID,
                PlaceRegistrationStatus.PENDING
        )).thenReturn(true);

        assertThatThrownBy(() -> service.approve(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest(null)))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MerchantOwnerErrorCode.UNIFIED_APPLICATION_REVIEW_REQUIRED));
    }

    @Test
    void rejectsAlreadyProcessedProfile() {
        User user = User.builder().id(USER_ID).role(UserRole.MERCHANT_OWNER).build();
        MerchantOwnerProfile profile = pendingProfile();
        profile.approve(ADMIN_USER_ID, NOW.minusMinutes(1));
        stubNow();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.approve(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest(null)))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.INVALID_PROFILE_STATE));
    }

    private MerchantOwnerProfile pendingProfile() {
        return MerchantOwnerProfile.pending(
                USER_ID,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                NOW.minusDays(1)
        );
    }

    private void stubNow() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-31T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
}
