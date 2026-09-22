package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityGrantRequest;
import com.typenull.pingdom.verification.api.dto.ScoutActivityEligibilityReviewRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfilePageResponse;
import com.typenull.pingdom.verification.api.dto.ScoutProfileRequest;
import com.typenull.pingdom.verification.api.dto.ScoutProfileResponse;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scout 신청과 프로필·활동 자격 심사를 트랜잭션으로 처리.
 * 사용자/프로필/자격의 잠금 조회와 상세 권한 확인을 조율하고 변경 이벤트·감사 이력·메트릭을 남김.
 * 이 서비스의 이벤트 발행과 메트릭 호출은 메서드 안에서 실행되며 자체 커밋 후 지연 처리는 없음.
 */
@Service
@RequiredArgsConstructor
public class ScoutProfileService {

    private final UserRepository userRepository;
    private final ScoutProfileRepository profileRepository;
    private final ScoutActivityEligibilityRepository eligibilityRepository;
    private final AdminRoleAuthorizationService adminRoleAuthorizationService;
    private final AdminAuditLogService auditLogService;
    private final ScoutFieldReportMetrics metrics;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 신청자 행을 먼저 잠가 프로필이 아직 없는 동시 신청도 같은 사용자 기준으로 순서화.
     * 프로필 존재 시 상태와 무관하게 재신청을 거부하고 최초 신청은 프로필·자격 모두 PENDING으로 저장.
     */
    @Transactional
    public ScoutProfileResponse apply(Long userId, ScoutProfileRequest request) {
        requireApplicantForUpdate(userId);
        if (profileRepository.findByUserIdForUpdate(userId).isPresent()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ScoutProfile profile = profileRepository.save(
                ScoutProfile.pending(userId, request.displayName(), request.introduction(), now)
        );
        ScoutActivityEligibility eligibility = eligibilityRepository.save(
                ScoutActivityEligibility.pending(userId, now)
        );
        eventPublisher.publishEvent(new ScoutProfileChangedEvent(
                userId, userId, null, profile.getStatus(), now
        ));
        metrics.recordProfileStatusUpdate(null, profile.getStatus());
        return ScoutProfileResponse.from(profile, eligibility);
    }

    /** 프로필과 활동 자격을 합쳐 반환. 두 모델 중 하나라도 없으면 해당 부재 오류를 전달. */
    @Transactional(readOnly = true)
    public ScoutProfileResponse get(Long userId) {
        return response(requireProfile(userId));
    }

    /**
     * 유효한 신청자와 프로필을 잠금 조회해 표시 정보를 수정.
     * 수정 가능 프로필 상태는 도메인이 판단하며 활동 자격 상태는 유지.
     */
    @Transactional
    public ScoutProfileResponse update(Long userId, ScoutProfileRequest request) {
        requireApplicantForUpdate(userId);
        ScoutProfile profile = requireProfileForUpdate(userId);
        try {
            profile.updateProfile(request.displayName(), request.introduction(), LocalDateTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_STATE);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_DETAILS);
        }
        return response(profile);
    }

    /**
     * SCOUT_REVIEW 권한으로 상태별 프로필을 수정 시각·사용자 ID 역순 조회.
     * 페이지 하한 1, 크기 1~100으로 보정하며 각 프로필에 자격 정보를 결합.
     */
    @Transactional(readOnly = true)
    public ScoutProfilePageResponse listForAdmin(
            Long adminUserId,
            ScoutProfileStatus status,
            int page,
            int limit
    ) {
        requireScoutReviewPermission(adminUserId);
        PageRequest pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId"))
        );
        Page<ScoutProfile> profiles = status == null
                ? profileRepository.findAll(pageable)
                : profileRepository.findAllByStatus(status, pageable);
        return new ScoutProfilePageResponse(
                profiles.getContent().stream().map(this::response).toList(),
                profiles.getNumber() + 1,
                profiles.getSize(),
                profiles.getTotalElements(),
                profiles.getTotalPages(),
                profiles.hasNext()
        );
    }

    /** 상세 심사 권한을 확인하고 지정 Scout의 프로필과 활동 자격을 반환. */
    @Transactional(readOnly = true)
    public ScoutProfileResponse getForAdmin(Long adminUserId, Long scoutUserId) {
        requireScoutReviewPermission(adminUserId);
        return response(requireProfile(scoutUserId));
    }

    /** 심사 권한과 대상 계정의 탈퇴·정지를 확인한 뒤 잠근 프로필을 활성화. 활동 자격은 별도 부여. */
    @Transactional
    public ScoutProfileResponse approveProfile(
            Long adminUserId,
            Long scoutUserId,
            ScoutProfileReviewRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        requireEligibleTargetForActivation(scoutUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        return changeProfileStatus(adminUserId, scoutUserId, profile, request.reason(), ProfileDecision.ACTIVATE);
    }

    /** 심사 권한으로 프로필을 잠그고 사유와 함께 정지. 활동 자격 행은 직접 변경 대상에서 제외. */
    @Transactional
    public ScoutProfileResponse suspendProfile(
            Long adminUserId,
            Long scoutUserId,
            ScoutProfileReviewRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        return changeProfileStatus(adminUserId, scoutUserId, profile, request.reason(), ProfileDecision.SUSPEND);
    }

    /** 심사 권한으로 프로필을 잠그고 사유와 함께 회수. 허용 상태 전이는 도메인이 검증. */
    @Transactional
    public ScoutProfileResponse revokeProfile(
            Long adminUserId,
            Long scoutUserId,
            ScoutProfileReviewRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        return changeProfileStatus(adminUserId, scoutUserId, profile, request.reason(), ProfileDecision.REVOKE);
    }

    /**
     * 대상 계정과 활성 프로필을 확인한 뒤 활동 자격을 잠가 기간을 부여.
     * 기간·상태 오류를 구분하며 감사 이력과 변경 이벤트, 상태 메트릭을 기록.
     */
    @Transactional
    public ScoutProfileResponse grantEligibility(
            Long adminUserId,
            Long scoutUserId,
            ScoutActivityEligibilityGrantRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        requireEligibleTargetForActivation(scoutUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        requireActiveProfile(profile);
        ScoutActivityEligibility eligibility = requireEligibilityForUpdate(scoutUserId);
        ScoutActivityEligibilityStatus beforeStatus = eligibility.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            eligibility.grant(adminUserId, request.eligibleFrom(), request.eligibleUntil(), now);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_STATE
            );
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_PERIOD
            );
        }
        recordEligibilityChange(
                adminUserId,
                scoutUserId,
                beforeStatus,
                eligibility,
                request.reason()
        );
        return response(profile);
    }

    /** 프로필 다음 자격 순으로 잠금 조회해 활동 자격을 정지하고 심사 결과를 기록. */
    @Transactional
    public ScoutProfileResponse suspendEligibility(
            Long adminUserId,
            Long scoutUserId,
            ScoutActivityEligibilityReviewRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        ScoutActivityEligibility eligibility = requireEligibilityForUpdate(scoutUserId);
        ScoutActivityEligibilityStatus beforeStatus = eligibility.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            eligibility.suspend(adminUserId, request.reason(), now);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_STATE
            );
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_PERIOD
            );
        }
        recordEligibilityChange(adminUserId, scoutUserId, beforeStatus, eligibility, request.reason());
        return response(profile);
    }

    /**
     * 프로필 다음 자격 순으로 잠금 조회해 자격을 회수.
     * 현재 구현은 잘못된 상태와 잘못된 인자 모두 자격 상태 오류로 변환.
     */
    @Transactional
    public ScoutProfileResponse revokeEligibility(
            Long adminUserId,
            Long scoutUserId,
            ScoutActivityEligibilityReviewRequest request
    ) {
        requireScoutReviewPermission(adminUserId);
        ScoutProfile profile = requireProfileForUpdate(scoutUserId);
        ScoutActivityEligibility eligibility = requireEligibilityForUpdate(scoutUserId);
        ScoutActivityEligibilityStatus beforeStatus = eligibility.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            eligibility.revoke(adminUserId, request.reason(), now);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_STATE
            );
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_ACTIVITY_ELIGIBILITY_STATE
            );
        }
        recordEligibilityChange(adminUserId, scoutUserId, beforeStatus, eligibility, request.reason());
        return response(profile);
    }

    /**
     * 프로필 상태 전이를 적용하고 감사·이벤트·메트릭을 남김.
     * 감사 이력의 변경 전 데이터에는 상태만 포함되며 이전 심사 시각·사유는 null로 기록됨.
     */
    private ScoutProfileResponse changeProfileStatus(
            Long adminUserId,
            Long scoutUserId,
            ScoutProfile profile,
            String reason,
            ProfileDecision decision
    ) {
        ScoutProfileStatus beforeStatus = profile.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            switch (decision) {
                case ACTIVATE -> profile.activate(adminUserId, now);
                case SUSPEND -> profile.suspend(adminUserId, reason, now);
                case REVOKE -> profile.revoke(adminUserId, reason, now);
            }
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_STATE);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_PROFILE_DETAILS);
        }

        auditLogService.record(
                adminUserId,
                AdminAuditAction.SCOUT_PROFILE_REVIEWED,
                AdminAuditTargetType.SCOUT_PROFILE,
                scoutUserId,
                reason,
                profileState(beforeStatus, null, null),
                profileState(profile.getStatus(), profile.getReviewedAt(), profile.getStatusReason())
        );
        eventPublisher.publishEvent(new ScoutProfileChangedEvent(
                adminUserId, scoutUserId, beforeStatus, profile.getStatus(), now
        ));
        metrics.recordProfileStatusUpdate(beforeStatus, profile.getStatus());
        return response(profile);
    }

    /**
     * 자격 상태 변경의 감사 이력·이벤트·메트릭을 기록.
     * 변경 전 감사 데이터는 상태만 보존하며 이전 기간과 사유는 이 메서드의 입력 범위 외.
     */
    private void recordEligibilityChange(
            Long adminUserId,
            Long scoutUserId,
            ScoutActivityEligibilityStatus beforeStatus,
            ScoutActivityEligibility eligibility,
            String reason
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        auditLogService.record(
                adminUserId,
                AdminAuditAction.SCOUT_ACTIVITY_ELIGIBILITY_REVIEWED,
                AdminAuditTargetType.SCOUT_ACTIVITY_ELIGIBILITY,
                scoutUserId,
                reason,
                eligibilityState(beforeStatus, null, null, null),
                eligibilityState(
                        eligibility.getStatus(),
                        eligibility.getEligibleFrom(),
                        eligibility.getEligibleUntil(),
                        eligibility.getStatusReason()
                )
        );
        eventPublisher.publishEvent(new ScoutActivityEligibilityChangedEvent(
                adminUserId, scoutUserId, beforeStatus, eligibility.getStatus(), now
        ));
        metrics.recordActivityEligibilityStatusUpdate(beforeStatus, eligibility.getStatus());
    }

    /** 프로필 사용자 ID로 자격을 조회해 통합 응답을 만들고 자격 부재는 오류로 처리. */
    private ScoutProfileResponse response(ScoutProfile profile) {
        ScoutActivityEligibility eligibility = eligibilityRepository.findById(profile.getUserId())
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_ACTIVITY_ELIGIBILITY_NOT_FOUND
                ));
        return ScoutProfileResponse.from(profile, eligibility);
    }

    /** 프로필이 없으면 SCOUT_PROFILE_NOT_FOUND를 반환. */
    private ScoutProfile requireProfile(Long scoutUserId) {
        return profileRepository.findById(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND
                ));
    }

    /** 프로필을 쓰기 잠금으로 조회하며 없으면 프로필 부재 오류로 거부. */
    private ScoutProfile requireProfileForUpdate(Long scoutUserId) {
        return profileRepository.findByUserIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND
                ));
    }

    /** 활동 자격을 쓰기 잠금으로 조회하며 누락된 자격은 전용 부재 오류로 거부. */
    private ScoutActivityEligibility requireEligibilityForUpdate(Long scoutUserId) {
        return eligibilityRepository.findByScoutUserIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_ACTIVITY_ELIGIBILITY_NOT_FOUND
                ));
    }

    /** 신청자 행을 잠그고 USER 역할·탈퇴·현재 정지 상태를 확인. */
    private User requireApplicantForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.getRole() != UserRole.USER
                || user.isWithdrawn()
                || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED);
        }
        return user;
    }

    /** 대상 계정을 잠그고 탈퇴 또는 현재 정지 여부를 확인. 계정 역할은 이 경로의 추가 검사 대상에서 제외. */
    private void requireEligibleTargetForActivation(Long scoutUserId) {
        User user = userRepository.findByIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED);
        }
    }

    /** 활동 자격 부여 전에 프로필 자체가 ACTIVE인지 요구. */
    private void requireActiveProfile(ScoutProfile profile) {
        if (profile.getStatus() != ScoutProfileStatus.ACTIVE) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_ACTIVITY_PROFILE_REQUIRED);
        }
    }

    /** Scout 심사용 상세 권한 SCOUT_REVIEW를 요구. */
    private void requireScoutReviewPermission(Long adminUserId) {
        adminRoleAuthorizationService.requirePermission(adminUserId, AdminPermission.SCOUT_REVIEW);
    }

    /** 감사 이력에 사용할 프로필 상태·심사 시각·사유를 순서가 있는 맵에 담음. */
    private Map<String, Object> profileState(
            ScoutProfileStatus status,
            LocalDateTime reviewedAt,
            String reason
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", status);
        state.put("reviewedAt", reviewedAt);
        state.put("reason", reason);
        return state;
    }

    /** 감사 이력에 사용할 자격 상태·기간·사유를 순서가 있는 맵에 담음. */
    private Map<String, Object> eligibilityState(
            ScoutActivityEligibilityStatus status,
            LocalDateTime eligibleFrom,
            LocalDateTime eligibleUntil,
            String reason
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", status);
        state.put("eligibleFrom", eligibleFrom);
        state.put("eligibleUntil", eligibleUntil);
        state.put("reason", reason);
        return state;
    }

    private enum ProfileDecision {
        ACTIVATE,
        SUSPEND,
        REVOKE
    }
}
