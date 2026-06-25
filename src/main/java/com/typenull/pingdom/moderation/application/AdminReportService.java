package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersItem;
import com.typenull.pingdom.moderation.api.dto.report.ReportedUsersResponse;

public interface AdminReportService {

    AdminReportActionResponse acceptReport(Long reportId, Long adminUserId);

    AdminReportActionResponse declineReport(Long reportId, Long adminUserId);

    ReportedUsersResponse getReportedUsers(int page, int limit, String keyword);

    ReportedUsersItem getReportedUser(Long reportId);
}
