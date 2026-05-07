package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminReportService;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.PictureReport;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private final PictureReportRepository pictureReportRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AdminReportActionResponse acceptReport(Long reportId) {
        PictureReport pictureReport = getPendingReport(reportId);
        User reportedUser = userRepository.findById(pictureReport.getMapImage().getUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        pictureReport.accept(now);
        // 신고 수락은 대상 사진 소유자 제재까지 하나의 처리로 본다.
        reportedUser.ban(pictureReport.getReason(), now);

        return new AdminReportActionResponse(
                pictureReport.getId(),
                pictureReport.getStatus(),
                reportedUser.getId(),
                reportedUser.isBanned(),
                pictureReport.getProcessedAt()
        );
    }

    @Override
    @Transactional
    public AdminReportActionResponse declineReport(Long reportId) {
        PictureReport pictureReport = getPendingReport(reportId);
        pictureReport.decline(LocalDateTime.now());
        boolean banned = userRepository.findById(pictureReport.getMapImage().getUserId())
                .map(User::isBanned)
                .orElse(false);

        return new AdminReportActionResponse(
                pictureReport.getId(),
                pictureReport.getStatus(),
                pictureReport.getMapImage().getUserId(),
                banned,
                pictureReport.getProcessedAt()
        );
    }

    private PictureReport getPendingReport(Long reportId) {
        PictureReport pictureReport = pictureReportRepository.findById(reportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        if (!pictureReport.isPending()) {
            throw new AdminException(AdminErrorCode.REPORT_ALREADY_PROCESSED);
        }

        return pictureReport;
    }
}
