package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
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

    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final AdminPostService adminPostService;
    private final UserSanctionCommandService userSanctionCommandService;
    private final AdminAuditLogService adminAuditLogService;
    private final ReportPolicyService reportPolicyService;
    private final Clock clock;

    @Override
    @Transactional
    public AdminReportActionResponse acceptReport(Long reportId, Long adminUserId) {
        PostReport postReport = getPendingReport(reportId);
        User reportedUser = userRepository.findById(postReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> beforeState = reportState(postReport, reportedUser.isCurrentlyBanned(now), false);
        postReport.accept(now);
        reportPolicyService.recordAccepted(postReport.getReporterUserId(), postReport.getReporterUsername());
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

        return new AdminReportActionResponse(
                postReport.getId(),
                postReport.getStatus(),
                postReport.getReportedUserId(),
                banned,
                postReport.getProcessedAt()
        );
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
                .map(report -> new ReportedUsersItem(
                        report.getId(),
                        report.getReporterUserId(),
                        report.getReporterUsername(),
                        report.getReportedImageId(),
                        report.getReportedUserId(),
                        report.getReason()
                ))
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
                keyword,
                numericKeyword,
                pageable
        );
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
