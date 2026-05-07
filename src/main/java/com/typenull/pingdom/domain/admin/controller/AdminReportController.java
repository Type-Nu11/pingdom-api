package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.report.AdminReportActionResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportDetailResponse;
import com.typenull.pingdom.domain.admin.dto.report.AdminReportSummaryResponse;
import com.typenull.pingdom.domain.admin.service.AdminReportQueryService;
import com.typenull.pingdom.domain.admin.service.AdminReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportQueryService adminReportQueryService;
    private final AdminReportService adminReportService;

    @GetMapping
    public List<AdminReportSummaryResponse> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return adminReportQueryService.listReports(page, limit);
    }

    @GetMapping("/{id}")
    public AdminReportDetailResponse getReport(@PathVariable Long id) {
        return adminReportQueryService.getReport(id);
    }

    @PostMapping("/{id}/accept")
    public AdminReportActionResponse acceptReport(@PathVariable Long id) {
        return adminReportService.acceptReport(id);
    }

    @PostMapping("/{id}/decline")
    public AdminReportActionResponse declineReport(@PathVariable Long id) {
        return adminReportService.declineReport(id);
    }
}
