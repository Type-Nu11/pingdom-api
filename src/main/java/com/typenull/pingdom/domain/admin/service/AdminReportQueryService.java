package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import org.springframework.data.domain.Page;

public interface AdminReportQueryService {

    Page<AdminReportSummaryResponse> listReports(int page, int limit);

    AdminReportDetailResponse getReport(Long reportId);
}
