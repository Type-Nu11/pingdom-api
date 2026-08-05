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

    @Test
    void withdrawnApplicantIsRejectedBeforeProfileLookup() {
        User withdrawn = user(1L);
        withdrawn.withdraw("withdrawn", "withdrawn@example.com", "탈퇴 요청", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED));

        verify(profileRepository, never()).findByUserIdForUpdate(1L);
    }

    @Test
    void bannedApplicantIsRejectedBeforeProfileLookup() {
        User banned = user(1L);
        banned.ban("신뢰도 확인 필요", NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED));

        verify(profileRepository, never()).findByUserIdForUpdate(1L);
    }

    @Test
    void pendingProfileCanBeUpdatedAndResponseKeepsEligibilityState() {
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

    @Test
    void suspendedProfileCannotBeUpdated() {
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

    @Test
    void adminWithoutScoutReviewPermissionIsRejectedBeforeTargetLookup() {
        doThrow(new AdminException(AdminErrorCode.ADMIN_PERMISSION_REQUIRED))
                .when(authorizationService).requirePermission(9L, AdminPermission.SCOUT_REVIEW);

        assertThatThrownBy(() -> service.getForAdmin(9L, 1L))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_PERMISSION_REQUIRED));

        verify(profileRepository, never()).findById(1L);
    }

    @Test
    void missingProfileIsReportedWithStableNotFoundCode() {
        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND));
    }

    @Test
    void invalidEligibilityPeriodIsRejectedWithoutPublishingReviewEvent() {
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

    @Test
    void approveCannotActivateProfileForBannedScout() {
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
