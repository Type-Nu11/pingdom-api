package com.typenull.pingdom.engagement.application.query;

import com.typenull.pingdom.engagement.api.dto.report.MyPostReportResponse;

public interface PostReportQueryService {

    MyPostReportResponse listMyReports(Long userId, int page, int limit);
}
