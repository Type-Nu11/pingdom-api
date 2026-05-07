package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.domain.admin.service.AdminReportQueryService;
import com.typenull.pingdom.domain.map.domain.PictureReport;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public List<AdminReportSummaryResponse> listReports(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return pictureReportRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(this::toSummaryResponse)
                .toList();
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
                pictureReport.getMapImage().getId(),
                pictureReport.getMapImage().getUserId(),
                pictureReport.getReporterUserId(),
                pictureReport.getReporterUsername(),
                pictureReport.getReason(),
                pictureReport.getStatus(),
                pictureReport.getProcessedAt()
        );
    }

    private AdminReportDetailResponse toDetailResponse(PictureReport pictureReport) {
        return new AdminReportDetailResponse(
                pictureReport.getId(),
                pictureReport.getMapImage().getId(),
                pictureReport.getMapImage().getUserId(),
                pictureReport.getMapImage().getImageUrl(),
                pictureReport.getReporterUserId(),
                pictureReport.getReporterUsername(),
                pictureReport.getReason(),
                pictureReport.getStatus(),
                pictureReport.getProcessedAt()
        );
    }
}
