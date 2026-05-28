package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminReportQueryService;
import com.typenull.pingdom.domain.map.domain.PictureReport;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportQueryServiceImpl implements AdminReportQueryService {

    private final PictureReportRepository pictureReportRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReportSummaryResponse> listReports(int page, int limit) {
        int safePage = Math.max(0, page);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return pictureReportRepository.findAllBy(
                        PageRequest.of(safePage, safeLimit, Sort.by(Sort.Direction.DESC, "id"))
                )
                .map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailResponse getReport(Long reportId) {
        PictureReport pictureReport = pictureReportRepository.findById(reportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        return toDetailResponse(pictureReport);
    }

    private AdminReportSummaryResponse toSummaryResponse(PictureReport pictureReport) {
        return new AdminReportSummaryResponse(
                pictureReport.getId(),
                pictureReport.getReportedImageId(),
                pictureReport.getReporterUsername(),
                pictureReport.getReason(),
                pictureReport.getStatus()
        );
    }

    private AdminReportDetailResponse toDetailResponse(PictureReport pictureReport) {
        return new AdminReportDetailResponse(
                pictureReport.getId(),
                pictureReport.getReportedImageId(),
                pictureReport.getReportedUserId(),
                pictureReport.getReportedImageUrl(),
                pictureReport.getReporterUserId(),
                pictureReport.getReporterUsername(),
                pictureReport.getReason(),
                pictureReport.getStatus(),
                pictureReport.getProcessedAt()
        );
    }
}
