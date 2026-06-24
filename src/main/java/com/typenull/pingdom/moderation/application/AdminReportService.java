package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;

public interface AdminReportService {

    AdminReportActionResponse acceptUserReport(Long reportId, Long adminUserId);

    AdminReportActionResponse declineUserReport(Long reportId);
}
