package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityGrantRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileReviewRequest;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.ScoutActivityEligibilityRepository;
import com.typenull.pingdom.verification.infrastructure.ScoutProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ScoutProfileServiceScenarioTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final ScoutProfileRepository profileRepository = org.mockito.Mockito.mock(ScoutProfileRepository.class);
    private final ScoutActivityEligibilityRepository eligibilityRepository =
            org.mockito.Mockito.mock(ScoutActivityEligibilityRepository.class);
    private final AdminRoleAuthorizationService authorizationService =
            org.mockito.Mockito.mock(AdminRoleAuthorizationService.class);
    private final AdminAuditLogService auditLogService = org.mockito.Mockito.mock(AdminAuditLogService.class);
    private final ScoutFieldReportMetrics metrics = org.mockito.Mockito.mock(ScoutFieldReportMetrics.class);
    private final ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private ScoutProfileService service;

    /** 고정 시계와 mock 의존성으로 서비스를 구성하고 프로필·자격 저장 mock은 입력 객체를 그대로 반환. */
    @BeforeEach
    void setUp() {
        service = new ScoutProfileService(
                userRepository,
                profileRepository,
                eligibilityRepository,
                authorizationService,
                auditLogService,
                metrics,
                eventPublisher,
                Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC)
        );
        when(profileRepository.save(any(ScoutProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eligibilityRepository.save(any(ScoutActivityEligibility.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 탈퇴 계정 신청의 계정 자격 오류와 프로필 잠금 조회 미호출 확인. */
    @Test
    void rejectWithdrawnApplicant() {
        User withdrawn = user(1L);
        withdrawn.withdraw("withdrawn", "withdrawn@example.com", "탈퇴 요청", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED));

        verify(profileRepository, never()).findByUserIdForUpdate(1L);
    }

    /** 현재 정지 계정 신청의 계정 자격 오류와 프로필 잠금 조회 미호출 확인. */
    @Test
    void rejectBannedApplicant() {
        User banned = user(1L);
        banned.ban("신뢰도 확인 필요", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED));

        verify(profileRepository, never()).findByUserIdForUpdate(1L);
    }

    /** 대기 프로필의 이름·소개 앞뒤 공백을 정리해 수정하고 프로필과 자격 PENDING 상태를 유지. */
    @Test
    void updatePendingProfile() {
        ScoutProfile profile = ScoutProfile.pending(1L, "기존 Scout", null, NOW);
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(1L, NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findById(1L)).thenReturn(Optional.of(eligibility));

        var response = service.update(1L, new ScoutProfileRequest(" 새 Scout ", " 새 소개 "));

        assertThat(response.displayName()).isEqualTo("새 Scout");
        assertThat(response.introduction()).isEqualTo("새 소개");
        assertThat(response.profileStatus()).isEqualTo(ScoutProfileStatus.PENDING);
        assertThat(response.activityEligibilityStatus()).isEqualTo(
                com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus.PENDING
        );
    }

    /** 정지 프로필 수정은 INVALID_SCOUT_PROFILE_STATE로 거부. */
    @Test
    void rejectSuspendedProfileUpdate() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW);
        profile.suspend(9L, "재검증 필요", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.update(1L, new ScoutProfileRequest("새 Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_STATE));
    }

    /**
     * 활동 자격이 정지되어도 프로필 자체가 ACTIVE이면 이름 수정이 가능.
     * 프로필 ACTIVE와 자격 SUSPENDED 상태는 그대로 반환해야 함.
     */
    @Test
    void updateDespiteSuspendedEligibility() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW.plusMinutes(1));
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(1L, NOW);
        eligibility.grant(9L, NOW.plusMinutes(1), null, NOW.plusMinutes(1));
        eligibility.suspend(9L, "현장 재검증", NOW.plusMinutes(2));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findById(1L)).thenReturn(Optional.of(eligibility));

        var response = service.update(1L, new ScoutProfileRequest("수정 Scout", null));

        assertThat(response.displayName()).isEqualTo("수정 Scout");
        assertThat(response.profileStatus()).isEqualTo(ScoutProfileStatus.ACTIVE);
        assertThat(response.activityEligibilityStatus()).isEqualTo(
                com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus.SUSPENDED
        );
    }

    /** 회수 프로필 수정은 프로필 상태 오류로 거부. */
    @Test
    void rejectRevokedProfileUpdate() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW.plusMinutes(1));
        profile.revoke(9L, "자격 회수", NOW.plusMinutes(2));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.update(1L, new ScoutProfileRequest("새 Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_STATE));
    }

    /**
     * 이미 회수된 프로필도 기존 신청으로 취급해 중복 오류를 반환.
     * 프로필과 활동 자격을 새로 저장하지 않아야 함.
     */
    @Test
    void rejectRevokedProfileReapplication() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW.plusMinutes(1));
        profile.revoke(9L, "자격 회수", NOW.plusMinutes(2));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("새 Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ALREADY_EXISTS));

        verify(profileRepository, never()).save(any(ScoutProfile.class));
        verify(eligibilityRepository, never()).save(any(ScoutActivityEligibility.class));
    }

    /** 상세 심사 권한 검사 실패 시 동일 관리자 권한 오류 전달과 대상 프로필 조회 미호출 확인. */
    @Test
    void rejectUnauthorizedAdminLookup() {
        doThrow(new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED))
                .when(authorizationService).requirePermission(9L, AdminPermission.SCOUT_REVIEW);

        assertThatThrownBy(() -> service.getForAdmin(9L, 1L))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_PERMISSION_REQUIRED));

        verify(profileRepository, never()).findById(1L);
    }

    /** 본인 프로필 조회 결과가 없으면 SCOUT_PROFILE_NOT_FOUND를 반환. */
    @Test
    void reportMissingProfile() {
        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND));
    }

    /** 종료가 시작보다 빠른 기간의 자격 기간 오류와 변경 이벤트 미발행 확인. */
    @Test
    void rejectReversedEligibilityPeriod() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW);
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(1L, NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findByScoutUserIdForUpdate(1L)).thenReturn(Optional.of(eligibility));

        assertThatThrownBy(() -> service.grantEligibility(
                9L,
                1L,
                new ScoutActivityEligibilityGrantRequest(NOW.plusDays(2), NOW.plusDays(1), "기간 오류")
        )).isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_PERIOD));

        verify(eventPublisher, never()).publishEvent(any());
    }

    /** 정지 계정의 프로필 승인은 계정 자격 오류로 거부하며 프로필 잠금 조회 전에 끝나야 함. */
    @Test
    void rejectBannedProfileApproval() {
        User banned = user(1L);
        banned.ban("심사 보류", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> service.approveProfile(
                9L, 1L, new ScoutProfileReviewRequest("승인 시도")
        )).isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED));

        verify(profileRepository, never()).findByUserIdForUpdate(1L);
    }

    /** 지정 ID의 활성·미정지 일반 계정을 만들어 신청자/심사 대상 조건을 제공. */
    private User user(long id) {
        return User.builder()
                .id(id)
                .username("scout-user-" + id)
                .email("scout-user-" + id + "@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .banned(false)
                .build();
    }
}
