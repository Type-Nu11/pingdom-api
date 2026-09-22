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

    /**
     * 통합 신청이 없는 대기 프로필을 승인하면 활성 상태와 심사자·사유가 저장되는지 검증.
     * 사용자 역할을 점주로 올리고 접근 상태 캐시를 비우는 처리도 확인.
     */
    @Test
    void approvesPendingStandaloneProfile() {
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

    /**
     * 대기 프로필을 사유와 함께 거절하면 거절 상태와 심사 사유가 반영되는지 검증.
     * 기존 관광객 혜택을 닫고 점주 장소 연결을 삭제하는 후속 처리도 확인.
     */
    @Test
    void rejectsPendingStandaloneProfile() {
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

    /**
     * 공백만 있는 거절 사유는 심사 처리 전에 INVALID_REVIEW_REASON 오류로 거부되는지 검증.
     */
    @Test
    void rejectsBlankReviewReason() {
        assertThatThrownBy(() -> service.reject(ADMIN_USER_ID, USER_ID, new MerchantOwnerReviewRequest(" ")))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.INVALID_REVIEW_REASON));
    }

    /**
     * 대기 중인 통합 점주 신청이 있으면 프로필 직접 승인을 통합 신청 심사 필요 오류로 차단하는지 검증.
     */
    @Test
    void blocksReviewDuringUnifiedApplication() {
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

    /**
     * 이미 승인된 프로필에 다시 승인을 요청하면 INVALID_PROFILE_STATE 오류를 반환하는지 검증.
     */
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

    /**
     * 심사 메타데이터와 상태 전이를 확인할 수 있도록 전날 신청된 대기 점주 프로필을 생성.
     */
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

    /**
     * 심사 시각과 혜택 종료 시각을 일정하게 비교하도록 UTC Clock 값을 고정.
     */
    private void stubNow() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-31T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
}
