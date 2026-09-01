package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportResponse;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportService.LocationAnalysisPdf;
import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationAnalysisReportArchiveService {

    private final LocationAnalysisReportRepository reportRepository;
    private final Clock clock;

    @Transactional
    public LocationAnalysisReportResponse archive(LocationAnalysisRequest request, LocationAnalysisPdf pdf) {
        LocationAnalysisReport report = LocationAnalysisReport.create(
                pdf.reportId(), pdf.reportName(), request.getCategory(), request.getRegion(),
                request.getTargetCustomerGroup(), request.getOperatingHours(), normalizeEmail(request.getEmail()),
                Boolean.TRUE.equals(request.getPrivacyConsent()), pdf.publishedDate(),
                pdf.analysisBasisDate(), pdf.html(), pdf.content(), LocalDateTime.now(clock)
        );
        return LocationAnalysisReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<LocationAnalysisReportResponse> list(String email) {
        return reportRepository.findSummariesByEmail(normalizeEmail(email)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationAnalysisReportResponse get(String reportId, String email) {
        return reportRepository.findSummaryByReportIdAndEmail(reportId, normalizeEmail(email))
                .map(this::toResponse)
                .orElseThrow(() -> notFound());
    }

    @Transactional(readOnly = true)
    public LocationAnalysisPdfDownload download(String reportId, String email) {
        LocationAnalysisReport report = find(reportId, email);
        return new LocationAnalysisPdfDownload(
                report.getReportId(), report.getReportName(), report.getPublishedDate(), report.getVersion(),
                report.getPdfContent()
        );
    }

    @Transactional(readOnly = true)
    public String html(String reportId, String email) {
        return reportRepository.findHtmlByReportIdAndEmail(reportId, normalizeEmail(email))
                .orElseThrow(() -> notFound());
    }

    @Transactional
    public LocationAnalysisReportResponse update(
            String reportId,
            String email,
            LocationAnalysisReportUpdateRequest request
    ) {
        LocationAnalysisReport report = find(reportId, email);
        report.update(request.reportName(), normalizeEmail(request.email()), LocalDateTime.now(clock));
        return LocationAnalysisReportResponse.from(report);
    }

    @Transactional
    public void delete(String reportId, String email) {
        reportRepository.delete(find(reportId, email));
    }

    private LocationAnalysisReport find(String reportId, String email) {
        return reportRepository.findByReportIdAndEmail(reportId, normalizeEmail(email))
                .orElseThrow(this::notFound);
    }

    private AnalysisReportException notFound() {
        return new AnalysisReportException(AnalysisReportErrorCode.ANALYSIS_REPORT_NOT_FOUND, null);
    }

    public record LocationAnalysisPdfDownload(
            String reportId,
            String reportName,
            java.time.LocalDate publishedDate,
            long version,
            byte[] content
    ) {
        public LocationAnalysisPdfDownload(String reportId, byte[] content) {
            this(reportId, null, null, 0, content);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private LocationAnalysisReportResponse toResponse(
            LocationAnalysisReportRepository.SummaryView report
    ) {
        return LocationAnalysisReportResponse.fromSummary(
                report.getReportId(), report.getReportName(), report.getCategory(), report.getRegion(),
                report.getTargetCustomerGroup(), report.getOperatingHours(), report.getEmail(),
                report.isPrivacyConsent(), report.getPublishedDate(), report.getAnalysisBasisDate(),
                report.getCreatedAt()
        );
    }
}
