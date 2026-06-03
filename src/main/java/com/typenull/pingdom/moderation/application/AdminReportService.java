package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;

public interface AdminReportService {

    AdminReportActionResponse acceptReport(Long reportId);

    AdminReportActionResponse declineReport(Long reportId);
}
