package com.typenull.pingdom.moderation.application.service.report;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.moderation.api.dto.report.AdminPostReportBulkActionResponse;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersItem;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.AdminReportService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 신고의 승인·반려를 신고자 정책·사진 노출·사용자 제재·감사 기록·알림 outbox와 연결.
 * 승인은 REPORT_REVIEW·USER_SANCTION 권한을 모두 요구하며 반려에는 신고자 정책 갱신 포함.
 * 대기 상태 확인은 일반 조회이므로 동시 처리 간 경쟁 가능.
 */
@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private static final String BULK_REPORT_ACCEPTED_REASON = "REPORT_BULK_ACCEPTED";
    private static final String BULK_REPORT_DECLINED_REASON = "REPORT_BULK_DECLINED";

    private final PostReportRepository postReportRepository;
    private final MapImageRepository mapImageRepository;
    private final UserRepository userRepository;
    private final AdminPostService adminPostService;
    private final UserSanctionCommandService userSanctionCommandService;
    private final AdminAuditLogService adminAuditLogService;
    private final ReportPolicyService reportPolicyService;
    private final AdminNotificationOutboxPublisher adminNotificationOutboxPublisher;
    private final AdminRoleAuthorizationService authorizationService;
    private final Clock clock;

    /**
     * 대기 신고를 승인하고 신고자 승인 집계·신뢰 정책을 갱신한 뒤 피신고자를 무기한 정지하고 사진을 숨김.
     * 관련 사용자·게시글이 없거나 후속 처리가 실패하면 같은 트랜잭션의 변경을 롤백.
     */
    @Override
    @Transactional
    public AdminReportActionResponse acceptReport(Long reportId, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        // 승인에 포함된 사용자 제재 변경도 작업 시작 전에 인가.
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_SANCTION);
        PostReport postReport = getPendingReport(reportId);
        User reportedUser = userRepository.findById(postReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        User reporter = userRepository.findById(postReport.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));


        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> beforeState = reportState(postReport, reportedUser.isCurrentlyBanned(now), false);
        postReport.accept(now);
        reportPolicyService.recordAccepted(postReport.getReporterUserId(), postReport.getReporterUsername());
        reporter.increaseReportCount();
        // 신고 수락은 대상 사진 숨김과 소유자 제재까지 하나의 처리로 판단.
        userSanctionCommandService.applyBan(reportedUser, postReport.getReason(), now, null, adminUserId);
        adminPostService.hidePost(postReport.getReportedImageId(), "REPORT_ACCEPTED", adminUserId);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.REPORT_ACCEPTED,
                AdminAuditTargetType.REPORT,
                postReport.getId(),
                postReport.getReason(),
                beforeState,
                reportState(postReport, reportedUser.isCurrentlyBanned(now), true)
        );
        adminNotificationOutboxPublisher.publishReportProcessed(
                postReport.getId(),
                postReport.getReportedImageId(),
                postReport.getStatus()
        );

        return new AdminReportActionResponse(
                postReport.getId(),
                postReport.getStatus(),
                reportedUser.getId(),
                reportedUser.isCurrentlyBanned(now),
                postReport.getProcessedAt()
        );
    }

    /**
     * REPORT_REVIEW 권한과 PENDING 신고·신고자 존재를 확인하고 반려 상태·신고자 신뢰 정책 갱신.
     * 감사 기록·관리자 알림 outbox를 같은 트랜잭션에 저장하며 피신고자 제재·게시글 숨김은 유지.
     * 신고 부재·기처리·신고자 부재는 오류 처리하며 정책 평가에 따라 신고자의 신고 제한 변경 가능.
     */
    @Override
    @Transactional
    public AdminReportActionResponse declineReport(Long reportId, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        PostReport postReport = getPendingReport(reportId);
        LocalDateTime now = LocalDateTime.now(clock);
        User reporter = userRepository.findById(postReport.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        boolean beforeBanned = userRepository.findById(postReport.getReportedUserId())
                .map(user -> user.isCurrentlyBanned(now))
                .orElse(false);
        Map<String, Object> beforeState = reportState(postReport, beforeBanned, false);

        postReport.decline(now);
        reportPolicyService.recordDeclined(postReport.getReporterUserId(), postReport.getReporterUsername(), now);
        boolean banned = userRepository.findById(postReport.getReportedUserId())
                .map(user -> user.isCurrentlyBanned(now))
                .orElse(false);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.REPORT_DECLINED,
                AdminAuditTargetType.REPORT,
                postReport.getId(),
                postReport.getReason(),
                beforeState,
                reportState(postReport, banned, false)
        );
        adminNotificationOutboxPublisher.publishReportProcessed(
                postReport.getId(),
                postReport.getReportedImageId(),
                postReport.getStatus()
        );

        reporter.getUnacceptedReportPercent();

        return new AdminReportActionResponse(
                postReport.getId(),
                postReport.getStatus(),
                postReport.getReportedUserId(),
                banned,
                postReport.getProcessedAt()
        );
    }

    /**
     * 현재 조회되는 동일 사진의 PENDING 신고를 모두 승인하되 사용자 제재·사진 숨김은 한 번 수행.
     * 처리 대상이 없으면 오류 반환. 이전 성공 응답 재생 없이 반복 요청도 현재 대기 신고를 기준으로 처리.
     */
    @Override
    @Transactional
    public AdminPostReportBulkActionResponse acceptPostReports(Long postId, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        // 승인에 포함된 사용자 제재 변경도 작업 시작 전에 인가.
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_SANCTION);
        MapImage mapImage = getPost(postId);
        List<PostReport> pendingReports = getPendingReports(postId);
        if (pendingReports.isEmpty()) {
            throw new AdminException(AdminErrorCode.PENDING_REPORT_NOT_FOUND);
        }

        PostReport firstReport = pendingReports.getFirst();
        User reportedUser = userRepository.findById(firstReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        boolean beforeBanned = reportedUser.isCurrentlyBanned(now);
        boolean beforePostHidden = !mapImage.isVisible();
        Map<Long, Map<String, Object>> beforeStates = reportStates(pendingReports, beforeBanned, beforePostHidden);
        Map<Long, User> reportersById = loadReportersById(pendingReports);

        for (PostReport report : pendingReports) {
            User reporter = getReporter(report, reportersById);
            report.accept(now);
            reportPolicyService.recordAccepted(report.getReporterUserId(), report.getReporterUsername());
            reporter.increaseReportCount();
        }

        userSanctionCommandService.applyBan(reportedUser, BULK_REPORT_ACCEPTED_REASON, now, null, adminUserId);
        adminPostService.hidePost(postId, BULK_REPORT_ACCEPTED_REASON, adminUserId);

        boolean afterBanned = reportedUser.isCurrentlyBanned(now);
        boolean afterPostHidden = !mapImage.isVisible();
        for (PostReport report : pendingReports) {
            adminAuditLogService.record(
                    adminUserId,
                    AdminAuditAction.REPORT_ACCEPTED,
                    AdminAuditTargetType.REPORT,
                    report.getId(),
                    BULK_REPORT_ACCEPTED_REASON,
                    beforeStates.get(report.getId()),
                    reportState(report, afterBanned, afterPostHidden)
            );
            adminNotificationOutboxPublisher.publishReportProcessed(
                    report.getId(),
                    report.getReportedImageId(),
                    report.getStatus()
            );
        }

        return toBulkActionResponse(mapImage, PostReportStatus.ACCEPTED, pendingReports.size(), now);
    }

    /**
     * REPORT_REVIEW 권한을 확인하고 존재하는 게시글의 현재 PENDING 신고를 모두 반려.
     * 대기 신고가 없으면 거절하고 각 신고자의 정책·감사 기록·관리자 알림 outbox를 같은 트랜잭션에 반영.
     * 게시글 숨김과 피신고자 제재 상태는 유지하며 관련 사용자가 없거나 후속 처리가 실패하면 전체 변경을 롤백.
     */
    @Override
    @Transactional
    public AdminPostReportBulkActionResponse declinePostReports(Long postId, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        MapImage mapImage = getPost(postId);
        List<PostReport> pendingReports = getPendingReports(postId);
        if (pendingReports.isEmpty()) {
            throw new AdminException(AdminErrorCode.PENDING_REPORT_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean postHidden = !mapImage.isVisible();
        PostReport firstReport = pendingReports.getFirst();
        User reportedUser = userRepository.findById(firstReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        boolean beforeBanned = reportedUser.isCurrentlyBanned(now);
        Map<Long, Map<String, Object>> beforeStates = reportStates(pendingReports, beforeBanned, postHidden);
        Map<Long, User> reportersById = loadReportersById(pendingReports);

        for (PostReport report : pendingReports) {
            getReporter(report, reportersById);
            report.decline(now);
            reportPolicyService.recordDeclined(report.getReporterUserId(), report.getReporterUsername(), now);
        }

        for (PostReport report : pendingReports) {
            adminAuditLogService.record(
                    adminUserId,
                    AdminAuditAction.REPORT_DECLINED,
                    AdminAuditTargetType.REPORT,
                    report.getId(),
                    BULK_REPORT_DECLINED_REASON,
                    beforeStates.get(report.getId()),
                    reportState(report, beforeBanned, postHidden)
            );
            adminNotificationOutboxPublisher.publishReportProcessed(
                    report.getId(),
                    report.getReportedImageId(),
                    report.getStatus()
            );
        }

        return toBulkActionResponse(mapImage, PostReportStatus.DECLINED, pendingReports.size(), now);
    }

    /**
     * PENDING 신고를 검색어와 숫자로 해석 가능한 ID 조건으로 조회. 신고 단위 결과이므로 동일 사용자 중복 가능.
     * LIKE 특수문자는 이스케이프하고 page는 1 이상·limit는 1~100으로 보정해 신고 ID와 신고자·피신고자 정보 반환.
     */
    @Transactional(readOnly = true)
    public ReportedUsersResponse getReportedUsers(int page, int limit, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int targetPage = safePage - 1;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Long numericKeyword = parseLongKeyword(safeKeyword);

        PageRequest pageable = PageRequest.of(targetPage, safeLimit);
        Page<PostReport> reportPage = loadReportedUsersPage(safeKeyword, numericKeyword, pageable);

        List<ReportedUsersItem> users = reportPage.getContent().stream()
                .map(this::toReportedUsersItem)
                .toList();

        return ReportedUsersResponse.of(
                users,
                safePage,
                safeLimit,
                reportPage.getTotalElements(),
                reportPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ReportedUsersItem getReportedUser(Long reportId) {
        PostReport report = postReportRepository.findById(reportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        return toReportedUsersItem(report);
    }

    private ReportedUsersItem toReportedUsersItem(PostReport report) {
        return new ReportedUsersItem(
                report.getId(),
                report.getReporterUserId(),
                report.getReporterUsername(),
                report.getReportedImageId(),
                report.getReportedUserId(),
                report.getReason()
        );
    }

    private PostReport getPendingReport(Long reportId) {
        PostReport postReport = postReportRepository.findById(reportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        if (!postReport.isPending()) {
            throw new AdminException(AdminErrorCode.REPORT_ALREADY_PROCESSED);
        }

        return postReport;
    }

    private MapImage getPost(Long postId) {
        return mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));
    }

    private List<PostReport> getPendingReports(Long postId) {
        return postReportRepository.findAllByReportedImageIdAndStatusOrderByIdAsc(
                postId,
                PostReportStatus.PENDING
        );
    }

    private Map<Long, Map<String, Object>> reportStates(
            List<PostReport> reports,
            boolean reportedUserBanned,
            boolean postHidden
    ) {
        Map<Long, Map<String, Object>> states = new LinkedHashMap<>();
        for (PostReport report : reports) {
            states.put(report.getId(), reportState(report, reportedUserBanned, postHidden));
        }
        return states;
    }

    private Map<Long, User> loadReportersById(List<PostReport> reports) {
        Map<Long, User> reportersById = new LinkedHashMap<>();
        for (PostReport report : reports) {
            reportersById.putIfAbsent(report.getReporterUserId(), null);
        }

        userRepository.findAllById(reportersById.keySet())
                .forEach(reporter -> reportersById.put(reporter.getId(), reporter));

        reportersById.forEach((reporterUserId, reporter) -> {
            if (reporter == null) {
                throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
            }
        });
        return reportersById;
    }

    private User getReporter(PostReport report, Map<Long, User> reportersById) {
        User reporter = reportersById.get(report.getReporterUserId());
        if (reporter == null) {
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }
        return reporter;
    }

    private AdminPostReportBulkActionResponse toBulkActionResponse(
            MapImage mapImage,
            PostReportStatus status,
            int processedReportCount,
            LocalDateTime processedAt
    ) {
        return new AdminPostReportBulkActionResponse(
                mapImage.getId(),
                status,
                processedReportCount,
                mapImage.getVisibilityStatus(),
                mapImage.getHiddenAt(),
                mapImage.getHiddenReason(),
                processedAt
        );
    }

    private Long parseLongKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Page<PostReport> loadReportedUsersPage(String keyword, Long numericKeyword, Pageable pageable) {
        if (keyword.isBlank()) {
            return postReportRepository.findByStatus(PostReportStatus.PENDING, pageable);
        }

        return postReportRepository.searchPendingReports(
                PostReportStatus.PENDING,
                escapeLikeKeyword(keyword),
                numericKeyword,
                pageable
        );
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Map<String, Object> reportState(PostReport postReport, boolean reportedUserBanned, boolean postHidden) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportId", postReport.getId());
        state.put("status", postReport.getStatus());
        state.put("reportedUserId", postReport.getReportedUserId());
        state.put("reportedImageId", postReport.getReportedImageId());
        state.put("reportedUserBanned", reportedUserBanned);
        state.put("postHidden", postHidden);
        state.put("processedAt", postReport.getProcessedAt());
        return state;
    }
}
