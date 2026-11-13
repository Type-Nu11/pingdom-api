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
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    /**
     * 최초 신청은 사용자 1의 프로필과 활동 자격을 모두 PENDING으로 반환.
     * 프로필 변경 이벤트와 초기 상태 메트릭 호출을 확인.
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

    /** 기존 프로필이 있는 경우 중복 신청 오류와 프로필·자격 미저장 확인. */
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
     * 관리자 승인은 SCOUT_REVIEW 권한 확인과 감사 기록을 호출.
     * 프로필만 ACTIVE로 바뀌며 활동 자격은 PENDING을 유지해야 함.
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
     * 활성 프로필에 30일 활동 자격을 부여하면 ELIGIBLE이 됨.
     * 자격 변경 이벤트와 PENDING → ELIGIBLE 메트릭 호출을 확인.
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
     * 미승인 프로필에 자격 부여를 요청하면 활성 프로필 필요 오류로 거부.
     * 자격 잠금 조회까지 진행하지 않아야 함.
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

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void loadsPageEligibilityInOneBatch(int count) {
        var pageable = PageRequest.of(0, count,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId")));
        var profiles = IntStream.rangeClosed(1, count)
                .mapToObj(index -> ScoutProfile.pending((long) index, "Scout-" + index, null, NOW))
                .toList();
        var ids = profiles.stream().map(ScoutProfile::getUserId).toList();
        var eligibilities = ids.stream().map(id -> ScoutActivityEligibility.pending(id, NOW)).toList();
        when(profileRepository.findAll(pageable)).thenReturn(new PageImpl<>(profiles, pageable, count + 1));
        when(eligibilityRepository.findAllById(ids)).thenReturn(eligibilities);

        var result = service.listForAdmin(9L, null, 1, count);

        assertThat(result.profiles()).extracting(item -> item.userId()).containsExactlyElementsOf(ids);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(count);
        assertThat(result.totalCount()).isEqualTo(count + 1);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        verify(eligibilityRepository).findAllById(ids);
        verify(eligibilityRepository, never()).findById(any());
    }

    @Test
    void preservesProfileOrderAndStoredEligibilityState() {
        var pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId")));
        var firstProfile = ScoutProfile.pending(7L, "첫 Scout", null, NOW.plusMinutes(1));
        var secondProfile = ScoutProfile.pending(3L, "다음 Scout", null, NOW);
        var firstEligibility = ScoutActivityEligibility.pending(7L, NOW);
        var secondEligibility = ScoutActivityEligibility.pending(3L, NOW);
        secondEligibility.grant(9L, NOW.minusDays(2), NOW.minusDays(1), NOW.plusMinutes(2));
        when(profileRepository.findAll(pageable)).thenReturn(
                new PageImpl<>(List.of(firstProfile, secondProfile), pageable, 2));
        when(eligibilityRepository.findAllById(List.of(7L, 3L))).thenReturn(
                List.of(secondEligibility, firstEligibility));

        var result = service.listForAdmin(9L, null, 1, 20);

        assertThat(result.profiles()).extracting(item -> item.userId()).containsExactly(7L, 3L);
        assertThat(result.profiles().get(0).activityEligibilityStatus())
                .isEqualTo(ScoutActivityEligibilityStatus.PENDING);
        assertThat(result.profiles().get(1).activityEligibilityStatus())
                .isEqualTo(ScoutActivityEligibilityStatus.ELIGIBLE);
        assertThat(result.profiles().get(0).updatedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(result.profiles().get(1).updatedAt()).isEqualTo(NOW.plusMinutes(2));
        verify(eligibilityRepository, never()).findById(any());
    }

    @Test
    void rejectsWholePageWhenAnyEligibilityIsMissing() {
        var pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId")));
        var profiles = List.of(ScoutProfile.pending(2L, "둘", null, NOW),
                ScoutProfile.pending(1L, "하나", null, NOW));
        when(profileRepository.findAll(pageable)).thenReturn(new PageImpl<>(profiles, pageable, 2));
        when(eligibilityRepository.findAllById(List.of(2L, 1L)))
                .thenReturn(List.of(ScoutActivityEligibility.pending(2L, NOW)));

        assertThatThrownBy(() -> service.listForAdmin(9L, null, 1, 20))
                .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(VisitorVerificationErrorCode.SCOUT_ACTIVITY_ELIGIBILITY_NOT_FOUND));

        verify(eligibilityRepository, never()).findById(any());
    }

    @Test
    void skipsEligibilityLookupForEmptyPage() {
        var pageable = PageRequest.of(2, 20,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId")));
        when(profileRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 21));

        var result = service.listForAdmin(9L, null, 3, 20);

        assertThat(result.profiles()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.totalCount()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        verify(eligibilityRepository, never()).findAllById(any());
        verify(eligibilityRepository, never()).findById(any());
    }

    /** 지정 ID의 활성·미정지 일반 계정을 만들어 신청자/심사 대상 조건을 제공. */
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
