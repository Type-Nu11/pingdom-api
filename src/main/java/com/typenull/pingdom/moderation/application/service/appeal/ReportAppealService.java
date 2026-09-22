package com.typenull.pingdom.moderation.application.service.appeal;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealActionResponse;
import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealItem;
import com.typenull.pingdom.moderation.api.dto.appeal.AdminReportAppealResponse;
import com.typenull.pingdom.moderation.api.dto.appeal.ReportAppealCreateResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppeal;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피신고자의 이의제기를 접수하고 관리자 검토 결과를 신고·사진 노출·제재·감사 기록에 반영.
 * 이의제기 행 잠금 없이 일반 조회로 접수 가능 여부·미처리 여부 확인.
 */
@Service
@RequiredArgsConstructor
public class ReportAppealService {

    private final ReportAppealRepository reportAppealRepository;
    private final PostReportRepository postReportRepository;
    private final MapImageRepository mapImageRepository;
    private final UserRepository userRepository;
    private final AdminPostService adminPostService;
    private final UserSanctionCommandService userSanctionCommandService;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminRoleAuthorizationService authorizationService;
    private final Clock clock;

    /**
     * 피신고자 본인이며 신고가 승인되었거나 대상 사진이 숨김 상태일 때 접수.
     * 중복 거절 범위는 같은 신고·신청자의 SUBMITTED 건으로 한정하며 처리 완료 후 재신청은 허용.
     */
    @Transactional
    public ReportAppealCreateResponse submit(Long reportId, String reason, Long userId, String username) {
        PostReport report = postReportRepository.findById(reportId)
                .orElseThrow(() -> new MapException(MapErrorCode.REPORT_NOT_FOUND));
        if (!report.getReportedUserId().equals(userId)) {
            throw new MapException(MapErrorCode.REPORT_APPEAL_NOT_ALLOWED);
        }
        if (!canSubmitAppeal(report)) {
            throw new MapException(MapErrorCode.REPORT_APPEAL_NOT_ALLOWED);
        }
        if (reportAppealRepository.existsByReportIdAndAppellantUserIdAndStatus(
                reportId,
                userId,
                ReportAppealStatus.SUBMITTED
        )) {
            throw new MapException(MapErrorCode.REPORT_APPEAL_ALREADY_EXISTS);
        }

        ReportAppeal appeal = reportAppealRepository.saveAndFlush(ReportAppeal.builder()
                .reportId(report.getId())
                .postId(report.getReportedImageId())
                .appellantUserId(userId)
                .appellantUsername(username)
                .targetUserId(report.getReportedUserId())
                .reason(reason)
                .build());

        return new ReportAppealCreateResponse(
                appeal.getId(),
                appeal.getReportId(),
                appeal.getPostId(),
                appeal.getStatus(),
                appeal.getCreatedAt()
        );
    }

    private boolean canSubmitAppeal(PostReport report) {
        boolean acceptedReport = report.getStatus() == PostReportStatus.ACCEPTED;
        boolean hiddenPost = mapImageRepository.findById(report.getReportedImageId())
                .map(mapImage -> mapImage.getVisibilityStatus() == MapImageVisibilityStatus.AUTO_HIDDEN)
                .orElse(false);
        return acceptedReport || hiddenPost;
    }

    /**
     * 처리 상태가 주어지면 해당 상태의 이의제기만, 없으면 전체를 최신순 페이지로 반환. page는 1 이상·limit는 1~100으로 보정.
     */
    @Transactional(readOnly = true)
    public AdminReportAppealResponse list(ReportAppealStatus status, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(
                safePage - 1,
                safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<ReportAppeal> appealPage = status == null
                ? reportAppealRepository.findAll(pageable)
                : reportAppealRepository.findAllByStatus(status, pageable);
        List<AdminReportAppealItem> appeals = appealPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return AdminReportAppealResponse.of(
                appeals,
                safePage,
                safeLimit,
                appealPage.getTotalElements(),
                appealPage.getTotalPages()
        );
    }

    /**
     * 신고 심사와 사용자 제재 권한을 모두 확인한 후 신고·사진 복구.
     * 다른 승인 신고가 없고 복구된 신고 사유와 현재 제재 사유가 일치하는 경우에만 제재 해제.
     * 신고자 신뢰 점수·누적 신고 집계는 원상 복구 범위에서 제외.
     */
    @Transactional
    public AdminReportAppealActionResponse approve(Long appealId, String reason, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        // 승인에 포함된 사용자 제재 변경도 작업 시작 전에 인가.
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_SANCTION);
        ReportAppeal appeal = getSubmittedAppeal(appealId);
        PostReport report = postReportRepository.findById(appeal.getReportId())
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));
        User targetUser = userRepository.findById(appeal.getTargetUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> beforeState = appealState(appeal);

        report.restore(now);
        adminPostService.restorePost(appeal.getPostId(), reason, adminUserId);
        releaseBanIfNeeded(targetUser, report, reason, now, adminUserId);
        appeal.approve(adminUserId, reason, now);

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.APPEAL_APPROVED,
                AdminAuditTargetType.APPEAL,
                appeal.getId(),
                reason,
                beforeState,
                appealState(appeal)
        );

        return toActionResponse(appeal);
    }

    /**
     * REPORT_REVIEW 권한을 확인하고 SUBMITTED 이의제기만 거절해 결과·처리 시각 반환.
     * 대상 부재·기처리 상태는 오류 처리. 이의제기 전이와 감사 기록을 함께 저장하며 신고·게시글·사용자 제재는 유지.
     */
    @Transactional
    public AdminReportAppealActionResponse reject(Long appealId, String reason, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        ReportAppeal appeal = getSubmittedAppeal(appealId);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> beforeState = appealState(appeal);

        appeal.reject(adminUserId, reason, now);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.APPEAL_REJECTED,
                AdminAuditTargetType.APPEAL,
                appeal.getId(),
                reason,
                beforeState,
                appealState(appeal)
        );

        return toActionResponse(appeal);
    }

    private void releaseBanIfNeeded(
            User targetUser,
            PostReport report,
            String reason,
            LocalDateTime now,
            Long adminUserId
    ) {
        if (!targetUser.isCurrentlyBanned(now)) {
            return;
        }
        if (!shouldReleaseBan(targetUser, report)) {
            return;
        }
        Map<String, Object> beforeState = userSanctionState(targetUser, now);
        userSanctionCommandService.releaseBan(targetUser, reason, now, adminUserId);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.USER_BAN_RELEASED,
                AdminAuditTargetType.USER,
                targetUser.getId(),
                reason,
                beforeState,
                userSanctionState(targetUser, now)
        );
    }

    private boolean shouldReleaseBan(User targetUser, PostReport report) {
        boolean hasOtherAcceptedReport = postReportRepository.existsByReportedUserIdAndStatusAndIdNot(
                targetUser.getId(),
                PostReportStatus.ACCEPTED,
                report.getId()
        );
        // 이번 이의제기 외의 승인 신고가 남아 있으면 사용자 제재 유지.
        if (hasOtherAcceptedReport) {
            return false;
        }
        return postReportRepository.existsByReportedUserIdAndStatusAndReason(
                targetUser.getId(),
                PostReportStatus.RESTORED,
                targetUser.getBanReason()
        );
    }

    private ReportAppeal getSubmittedAppeal(Long appealId) {
        ReportAppeal appeal = reportAppealRepository.findById(appealId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.APPEAL_NOT_FOUND));
        if (!appeal.isSubmitted()) {
            throw new AdminException(AdminErrorCode.APPEAL_ALREADY_PROCESSED);
        }
        return appeal;
    }

    private AdminReportAppealItem toItem(ReportAppeal appeal) {
        return new AdminReportAppealItem(
                appeal.getId(),
                appeal.getReportId(),
                appeal.getPostId(),
                appeal.getAppellantUserId(),
                appeal.getAppellantUsername(),
                appeal.getTargetUserId(),
                appeal.getReason(),
                appeal.getStatus(),
                appeal.getAdminUserId(),
                appeal.getAdminReason(),
                appeal.getProcessedAt(),
                appeal.getCreatedAt()
        );
    }

    private AdminReportAppealActionResponse toActionResponse(ReportAppeal appeal) {
        return new AdminReportAppealActionResponse(
                appeal.getId(),
                appeal.getReportId(),
                appeal.getPostId(),
                appeal.getStatus(),
                appeal.getProcessedAt()
        );
    }

    private Map<String, Object> appealState(ReportAppeal appeal) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("appealId", appeal.getId());
        state.put("reportId", appeal.getReportId());
        state.put("postId", appeal.getPostId());
        state.put("appellantUserId", appeal.getAppellantUserId());
        state.put("status", appeal.getStatus());
        state.put("processedAt", appeal.getProcessedAt());
        return state;
    }

    private Map<String, Object> userSanctionState(User user, LocalDateTime now) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("userId", user.getId());
        state.put("banned", user.isCurrentlyBanned(now));
        state.put("banType", user.getBanType());
        state.put("bannedAt", user.getBannedAt());
        state.put("banExpiresAt", user.getBanExpiresAt());
        state.put("banReason", user.getBanReason());
        return state;
    }
}
