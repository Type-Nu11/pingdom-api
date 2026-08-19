package com.typenull.pingdom.moderation.application.service.report;

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
    private final Clock clock;

    @Override
    @Transactional
    /** 신고를 승인하고 대상 콘텐츠의 운영 상태 및 처리 이력을 갱신합니다. */
    public AdminReportActionResponse acceptReport(Long reportId, Long adminUserId) {
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
        // 신고 수락은 대상 사진 숨김과 소유자 제재까지 하나의 처리로 본다.
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

    @Override
    @Transactional
    public AdminReportActionResponse declineReport(Long reportId, Long adminUserId) {
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

    @Override
    @Transactional
    public AdminPostReportBulkActionResponse acceptPostReports(Long postId, Long adminUserId) {
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

    @Override
    @Transactional
    public AdminPostReportBulkActionResponse declinePostReports(Long postId, Long adminUserId) {
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
