package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.AdminReportService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final AdminPostService adminPostService;
    private final UserSanctionCommandService userSanctionCommandService;

    @Override
    @Transactional
    public AdminReportActionResponse acceptUserReport(Long reportId, Long adminUserId) {
        PostReport postReport = getPendingReport(reportId);
        User reportedUser = userRepository.findById(postReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        User reporter = userRepository.findById(postReport.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        postReport.accept(now);
        postReport.detachMapImage();
        // 신고 수락은 대상 사진 소유자 제재까지 하나의 처리로 본다.
        userSanctionCommandService.applyBan(reportedUser, postReport.getReason(), now, null, adminUserId);
        adminPostService.deletePost(postReport.getReportedImageId());
        reporter.increaseReportCount();

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
    public AdminReportActionResponse declineUserReport(Long reportId) {
        PostReport postReport = getPendingReport(reportId);
        User reporter = userRepository.findById(postReport.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        postReport.decline(now);
        boolean banned = userRepository.findById(postReport.getReportedUserId())
                .map(user -> user.isCurrentlyBanned(now))
                .orElse(false);
        reporter.increaseUnacceptedReportCount();

        return new AdminReportActionResponse(
                postReport.getId(),
                postReport.getStatus(),
                postReport.getReportedUserId(),
                banned,
                postReport.getProcessedAt()
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
}
