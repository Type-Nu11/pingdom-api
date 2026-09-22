package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityGrantRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileReviewRequest;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.event.ScoutActivityEligibilityChangedEvent;
import com.typenull.pingdom.verification.event.ScoutProfileChangedEvent;
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

class ScoutProfileServiceTest {

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

    /** 고정 시계와 mock 의존성으로 서비스를 구성하고 프로필·자격 저장 mock은 입력 객체를 그대로 반환한다. */
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

    /**
     * 최초 신청은 사용자 1의 프로필과 활동 자격을 모두 PENDING으로 반환한다.
     * 프로필 변경 이벤트와 초기 상태 메트릭 호출을 확인한다.
     */
    @Test
    void createPendingApplication() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());

        var response = service.apply(1L, new ScoutProfileRequest(" 현장 Scout ", " 장소를 확인합니다. "));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.profileStatus()).isEqualTo(ScoutProfileStatus.PENDING);
        assertThat(response.activityEligibilityStatus()).isEqualTo(ScoutActivityEligibilityStatus.PENDING);
        verify(eventPublisher).publishEvent(any(ScoutProfileChangedEvent.class));
        verify(metrics).recordProfileStatusUpdate(isNull(), eq(ScoutProfileStatus.PENDING));
    }

    /** 프로필이 이미 있으면 중복 신청 오류로 거부하고 프로필·자격 어느 쪽도 저장하지 않는다. */
    @Test
    void rejectDuplicateApplication() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(
                ScoutProfile.pending(1L, "기존 Scout", null, NOW)
        ));

        assertThatThrownBy(() -> service.apply(1L, new ScoutProfileRequest("새 Scout", null)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_PROFILE_ALREADY_EXISTS));

        verify(profileRepository, never()).save(any(ScoutProfile.class));
        verify(eligibilityRepository, never()).save(any(ScoutActivityEligibility.class));
    }

    /**
     * 관리자 승인은 SCOUT_REVIEW 권한 확인과 감사 기록을 호출한다.
     * 프로필만 ACTIVE로 바뀌며 활동 자격은 PENDING을 유지해야 한다.
     */
    @Test
    void approveProfileOnly() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(1L, NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findById(1L)).thenReturn(Optional.of(eligibility));

        var response = service.approveProfile(9L, 1L, new ScoutProfileReviewRequest("자료 확인 완료"));

        assertThat(response.profileStatus()).isEqualTo(ScoutProfileStatus.ACTIVE);
        assertThat(response.activityEligibilityStatus()).isEqualTo(ScoutActivityEligibilityStatus.PENDING);
        verify(authorizationService).requirePermission(
                eq(9L), eq(com.typenull.pingdom.identity.domain.admin.AdminPermission.SCOUT_REVIEW)
        );
        verify(auditLogService).record(
                eq(9L),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditAction.SCOUT_PROFILE_REVIEWED),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType.SCOUT_PROFILE),
                eq(1L),
                eq("자료 확인 완료"),
                any(),
                any()
        );
    }

    /**
     * 활성 프로필에 30일 활동 자격을 부여하면 ELIGIBLE이 된다.
     * 자격 변경 이벤트와 PENDING → ELIGIBLE 메트릭 호출을 확인한다.
     */
    @Test
    void grantApprovedProfileEligibility() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        ScoutActivityEligibility eligibility = ScoutActivityEligibility.pending(1L, NOW);
        profile.activate(9L, NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findByScoutUserIdForUpdate(1L)).thenReturn(Optional.of(eligibility));
        when(eligibilityRepository.findById(1L)).thenReturn(Optional.of(eligibility));

        var response = service.grantEligibility(
                9L,
                1L,
                new ScoutActivityEligibilityGrantRequest(NOW, NOW.plusDays(30), "활동 자격 승인")
        );

        assertThat(response.activityEligibilityStatus()).isEqualTo(ScoutActivityEligibilityStatus.ELIGIBLE);
        verify(eventPublisher).publishEvent(any(ScoutActivityEligibilityChangedEvent.class));
        verify(metrics).recordActivityEligibilityStatusUpdate(
                eq(ScoutActivityEligibilityStatus.PENDING),
                eq(ScoutActivityEligibilityStatus.ELIGIBLE)
        );
    }

    /**
     * 미승인 프로필에 자격 부여를 요청하면 활성 프로필 필요 오류로 거부한다.
     * 자격 잠금 조회까지 진행하지 않아야 한다.
     */
    @Test
    void rejectPendingProfileGrant() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(profileRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.grantEligibility(
                9L,
                1L,
                new ScoutActivityEligibilityGrantRequest(NOW, null, null)
        )).isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(VisitorVerificationErrorCode.SCOUT_ACTIVITY_PROFILE_REQUIRED));

        verify(eligibilityRepository, never()).findByScoutUserIdForUpdate(1L);
    }

    /** 지정 ID의 활성·미정지 일반 계정을 만들어 신청자/심사 대상 조건을 제공한다. */
    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("user-" + id)
                .email("user-" + id + "@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .banned(false)
                .build();
    }
}
