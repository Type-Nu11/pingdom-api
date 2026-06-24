package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportActionResponse;

public interface AdminReportService {

    AdminReportActionResponse acceptReport(Long reportId);

    AdminReportActionResponse declineReport(Long reportId);
}
