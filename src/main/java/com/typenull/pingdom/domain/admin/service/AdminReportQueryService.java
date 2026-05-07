package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import java.util.List;

public interface AdminReportQueryService {

    List<AdminReportSummaryResponse> listReports(int page, int limit);

    AdminReportDetailResponse getReport(Long reportId);
}
