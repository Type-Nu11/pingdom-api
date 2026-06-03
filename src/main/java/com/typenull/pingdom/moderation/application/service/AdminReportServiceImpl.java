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

    @Override
    @Transactional
    public AdminReportActionResponse acceptReport(Long reportId) {
        PostReport postReport = getPendingReport(reportId);
        User reportedUser = userRepository.findById(postReport.getReportedUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        postReport.accept(now);
        postReport.detachMapImage();
        // 신고 수락은 대상 사진 소유자 제재까지 하나의 처리로 본다.
        reportedUser.ban(postReport.getReason(), now);
        adminPostService.deletePost(postReport.getReportedImageId());

        return new AdminReportActionResponse(
                postReport.getId(),
                postReport.getStatus(),
                reportedUser.getId(),
                reportedUser.isBanned(),
                postReport.getProcessedAt()
        );
    }

    @Override
    @Transactional
    public AdminReportActionResponse declineReport(Long reportId) {
        PostReport postReport = getPendingReport(reportId);
        postReport.decline(LocalDateTime.now());
        boolean banned = userRepository.findById(postReport.getReportedUserId())
                .map(User::isBanned)
                .orElse(false);

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
