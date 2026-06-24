package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;

public interface AdminReportService {

    AdminReportActionResponse acceptReport(Long reportId, Long adminUserId);

    AdminReportActionResponse declineReport(Long reportId, Long adminUserId);
}
