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

    @Transactional(readOnly = true)
    public ScoutProfileResponse get(Long userId) {
        return response(requireProfile(userId));
    }

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

    @Transactional(readOnly = true)
    public ScoutProfileResponse getForAdmin(Long adminUserId, Long scoutUserId) {
        requireScoutReviewPermission(adminUserId);
        return response(requireProfile(scoutUserId));
    }

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

    private ScoutProfileResponse response(ScoutProfile profile) {
        ScoutActivityEligibility eligibility = eligibilityRepository.findById(profile.getUserId())
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_ACTIVITY_ELIGIBILITY_NOT_FOUND
                ));
        return ScoutProfileResponse.from(profile, eligibility);
    }

    private ScoutProfile requireProfile(Long scoutUserId) {
        return profileRepository.findById(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND
                ));
    }

    private ScoutProfile requireProfileForUpdate(Long scoutUserId) {
        return profileRepository.findByUserIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND
                ));
    }

    private ScoutActivityEligibility requireEligibilityForUpdate(Long scoutUserId) {
        return eligibilityRepository.findByScoutUserIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_ACTIVITY_ELIGIBILITY_NOT_FOUND
                ));
    }

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

    private void requireEligibleTargetForActivation(Long scoutUserId) {
        User user = userRepository.findByIdForUpdate(scoutUserId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_PROFILE_ACCOUNT_REQUIRED);
        }
    }

    private void requireActiveProfile(ScoutProfile profile) {
        if (profile.getStatus() != ScoutProfileStatus.ACTIVE) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_ACTIVITY_PROFILE_REQUIRED);
        }
    }

    private void requireScoutReviewPermission(Long adminUserId) {
        adminRoleAuthorizationService.requirePermission(adminUserId, AdminPermission.SCOUT_REVIEW);
    }

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
